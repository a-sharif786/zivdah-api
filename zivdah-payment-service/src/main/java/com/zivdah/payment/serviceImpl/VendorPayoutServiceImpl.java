package com.zivdah.payment.serviceImpl;

import com.zivdah.payment.client.AuthServiceClient;
import com.zivdah.payment.client.dto.VendorBankDetailsDto;
import com.zivdah.payment.dto.VendorPayoutResponseDto;
import com.zivdah.payment.entity.VendorPayout;
import com.zivdah.payment.enums.PayoutInitiator;
import com.zivdah.payment.enums.PayoutMode;
import com.zivdah.payment.enums.VendorPayoutStatus;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayClient;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutRequest;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import com.zivdah.payment.repository.VendorPayoutRepository;
import com.zivdah.payment.service.VendorPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VendorPayoutServiceImpl implements VendorPayoutService {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final AuthServiceClient authServiceClient;
    private final EcomWorldPayClient ecomWorldPayClient;

    // At most one open (REQUESTED/PROCESSING) payout per vendor — mirrors the DB-level
    // uq_vendor_payouts_open_per_vendor guard (V7); this is the friendly pre-check.
    private static final List<VendorPayoutStatus> OPEN_PAYOUT_STATUSES =
            List.of(VendorPayoutStatus.REQUESTED, VendorPayoutStatus.PROCESSING);

    private static final String GATEWAY_FAILURE_MESSAGE =
            "Payout could not be processed by the payment gateway right now. Please try again later or contact support.";

    @Override
    public Mono<VendorPayoutResponseDto> requestPayout(Long vendorId, BigDecimal amount) {
        return authServiceClient.getVendorBankDetails(vendorId)
                .flatMap(bank -> {
                    if (!bank.hasPayoutDestination()) {
                        return Mono.error(new RuntimeException(
                                "No bank account or UPI VPA on file — update your profile before requesting a payout"));
                    }
                    PayoutMode mode = bank.hasUpiVpa() ? PayoutMode.UPI : PayoutMode.IMPS;
                    return buildAndSavePayout(vendorId, amount, mode, bank, PayoutInitiator.VENDOR, vendorId);
                })
                .map(this::toDto);
    }

    @Override
    public Mono<VendorPayoutResponseDto> initiateAdminPayout(Long adminId, Long vendorId, BigDecimal amount, PayoutMode payoutMode) {
        return authServiceClient.getVendorBankDetails(vendorId)
                // Unlike the vendor path (always the caller's own, always-valid id), this is the
                // first caller passing an arbitrary vendor id — sanitized so a nonexistent id's
                // WebClientResponseException (which embeds auth-service's internal URL) never
                // reaches the client.
                .onErrorMap(WebClientResponseException.class, ex -> new RuntimeException("Vendor not found"))
                .flatMap(bank -> {
                    if (!bank.isVendor()) {
                        return Mono.error(new RuntimeException("Selected user is not a vendor"));
                    }
                    if (payoutMode == PayoutMode.UPI) {
                        if (!bank.hasUpiVpa()) {
                            return Mono.error(new RuntimeException("This vendor has no UPI VPA on file"));
                        }
                    } else if (!bank.hasBankAccount()) {
                        return Mono.error(new RuntimeException("This vendor has no bank account number/IFSC on file"));
                    }
                    return buildAndSavePayout(vendorId, amount, payoutMode, bank, PayoutInitiator.ADMIN, adminId);
                })
                .map(this::toDto);
    }

    // Shared by both creation paths: duplicate-payout guard, mode-specific field snapshot
    // (UPI -> payeeVpa only; everything else -> accountNo+ifscBankCode only), and save.
    // Snapshotted here (not re-read from the vendor's profile at approval time) so a later
    // profile edit can't retroactively change where an already-submitted request pays out to —
    // whatever the admin approves is exactly what was asked.
    private Mono<VendorPayout> buildAndSavePayout(Long vendorId, BigDecimal amount, PayoutMode mode,
                                                    VendorBankDetailsDto bank, PayoutInitiator initiator,
                                                    Long initiatedByUserId) {
        return vendorPayoutRepository.existsByVendorIdAndStatusIn(vendorId, OPEN_PAYOUT_STATUSES)
                .flatMap(hasOpenPayout -> {
                    if (Boolean.TRUE.equals(hasOpenPayout)) {
                        return Mono.error(new RuntimeException(
                                "A payout request is already pending for this vendor — wait for it to be resolved before requesting another."));
                    }
                    boolean useUpi = mode == PayoutMode.UPI;
                    VendorPayout payout = VendorPayout.builder()
                            .vendorId(vendorId)
                            .amount(amount)
                            .payoutMode(mode.name())
                            .accountNo(useUpi ? null : bank.getBankAccountNumber())
                            .ifscBankCode(useUpi ? null : bank.getBankIfscCode())
                            .payeeVpa(useUpi ? bank.getUpiVpa() : null)
                            .status(VendorPayoutStatus.REQUESTED)
                            .invoiceNumber(generateInvoiceNumber())
                            .initiatedByRole(initiator)
                            .initiatedByUserId(initiatedByUserId)
                            .requestedAt(LocalDateTime.now())
                            .build();
                    return vendorPayoutRepository.save(payout);
                });
    }

    @Override
    public Flux<VendorPayoutResponseDto> getPayoutsForVendor(Long vendorId) {
        return vendorPayoutRepository.findByVendorIdOrderByRequestedAtDesc(vendorId).map(this::toDto);
    }

    @Override
    public Flux<VendorPayoutResponseDto> getAllPayouts(VendorPayoutStatus status) {
        Flux<VendorPayout> source = status != null
                ? vendorPayoutRepository.findByStatusOrderByRequestedAtDesc(status)
                : vendorPayoutRepository.findAllByOrderByRequestedAtDesc();
        return source.map(this::toDto);
    }

    @Override
    public Mono<VendorPayoutResponseDto> approvePayout(Long payoutId, String ipAddress) {
        // Atomic compare-and-swap (REQUESTED -> PROCESSING) instead of read-then-write, so two
        // concurrent approve calls for the same payout can't both pass the status check and
        // double-submit it to the real gateway — only whichever call's UPDATE actually matches
        // a row (rowsUpdated > 0) proceeds to call EcomWorldPay.
        return vendorPayoutRepository.markProcessingIfRequested(payoutId, LocalDateTime.now())
                .flatMap(rowsUpdated -> rowsUpdated > 0
                        ? vendorPayoutRepository.findById(payoutId)
                                .flatMap(payout -> authServiceClient.getVendorBankDetails(payout.getVendorId())
                                        .flatMap(vendor -> submitPayout(payout, vendor, ipAddress)))
                        : vendorPayoutRepository.findById(payoutId)
                                .flatMap(p -> Mono.<VendorPayout>error(new RuntimeException("Only requested payouts can be approved")))
                                .switchIfEmpty(Mono.error(new RuntimeException("Payout not found"))))
                .map(this::toDto);
    }

    private Mono<VendorPayout> submitPayout(VendorPayout payout, VendorBankDetailsDto vendor, String ipAddress) {
        EcomWorldPayPayoutRequest request = EcomWorldPayPayoutRequest.builder()
                .invoiceNumber(payout.getInvoiceNumber())
                .merchantId(ecomWorldPayClient.getMerchantId())
                .customerName(vendor.getName())
                .phoneNumber(vendor.getMobile())
                .payoutMode(payout.getPayoutMode())
                .payoutAmount(payout.getAmount().stripTrailingZeros().toPlainString())
                .accountNo(payout.getAccountNo() != null ? payout.getAccountNo() : "")
                .ifscBankCode(payout.getIfscBankCode() != null ? payout.getIfscBankCode() : "")
                .secretKey(ecomWorldPayClient.getSecretKey())
                .apiKey(ecomWorldPayClient.getApiKey())
                .ipAddress(ipAddress)
                .payeeVpa(payout.getPayeeVpa() != null ? payout.getPayeeVpa() : "")
                .build();
        return ecomWorldPayClient.createPayout(request)
                .onErrorMap(this::sanitizeGatewayError)
                .flatMap(response -> applyCreateResult(payout, response.getData()));
    }

    // EcomWorldPayClient already log.error()s full detail (including the raw gateway response
    // body) before this runs — nothing server-side is lost, only what reaches the HTTP client
    // changes. Covers timeout, connection failure, malformed response, and non-2xx gateway
    // errors uniformly; no need to branch on exception type since the safe message is the same
    // regardless of cause.
    private RuntimeException sanitizeGatewayError(Throwable ex) {
        return new RuntimeException(GATEWAY_FAILURE_MESSAGE, ex);
    }

    private Mono<VendorPayout> applyCreateResult(VendorPayout payout, EcomWorldPayPayoutResponse.Data data) {
        String status = data != null ? data.getStatus() : null;
        payout.setGatewayStatus(status);
        payout.setGatewayDescription(data != null ? data.getDescription() : null);
        payout.setProcessedAt(LocalDateTime.now());
        payout.setUpdatedAt(LocalDateTime.now());
        if (status != null && status.equalsIgnoreCase("Processing")) {
            payout.setGatewayReferenceId(data.getReferenceId());
            payout.setStatus(VendorPayoutStatus.PROCESSING);
        } else {
            // e.g. "Invalid Request" — the gateway rejected it at submission time (bad
            // secret/api key, amount out of range for the chosen mode, etc.); Description
            // carries the gateway's own explanation.
            payout.setStatus(VendorPayoutStatus.FAILED);
        }
        return vendorPayoutRepository.save(payout);
    }

    @Override
    public Mono<VendorPayoutResponseDto> rejectPayout(Long payoutId, String reason) {
        return vendorPayoutRepository.findById(payoutId)
                .switchIfEmpty(Mono.error(new RuntimeException("Payout not found")))
                .flatMap(payout -> {
                    if (payout.getStatus() != VendorPayoutStatus.REQUESTED) {
                        return Mono.error(new RuntimeException("Only requested payouts can be rejected"));
                    }
                    payout.setStatus(VendorPayoutStatus.REJECTED);
                    payout.setRejectionReason(reason);
                    payout.setProcessedAt(LocalDateTime.now());
                    payout.setUpdatedAt(LocalDateTime.now());
                    return vendorPayoutRepository.save(payout);
                })
                .map(this::toDto);
    }

    @Override
    public Mono<VendorPayoutResponseDto> refreshPayoutStatus(Long payoutId) {
        return vendorPayoutRepository.findById(payoutId)
                .switchIfEmpty(Mono.error(new RuntimeException("Payout not found")))
                .flatMap(payout -> {
                    if (payout.getStatus() != VendorPayoutStatus.PROCESSING || payout.getGatewayReferenceId() == null) {
                        return Mono.just(payout);
                    }
                    return ecomWorldPayClient.checkPayoutStatus(payout.getGatewayReferenceId())
                            .onErrorMap(this::sanitizeGatewayError)
                            .flatMap(response -> applyStatusResult(payout, response.getData()));
                })
                .map(this::toDto);
    }

    @Override
    public Mono<Void> handlePayoutCallback(EcomWorldPayPayoutResponse callback) {
        EcomWorldPayPayoutResponse.Data data = callback.getData();
        if (data == null || data.getTransactionId() == null) {
            log.warn("EcomWorldPay payout callback received with no TransactionId — ignoring");
            return Mono.empty();
        }
        return vendorPayoutRepository.findByGatewayReferenceId(data.getTransactionId())
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("EcomWorldPay payout callback for unknown transaction {} — ignoring", data.getTransactionId())))
                .flatMap(payout -> applyStatusResult(payout, data))
                .then();
    }

    // Idempotent: a payout already in a terminal state (SUCCESS/FAILED/REJECTED) is left
    // untouched, whether this is called from a manual refresh or the async callback arriving
    // after (or racing) that refresh.
    private Mono<VendorPayout> applyStatusResult(VendorPayout payout, EcomWorldPayPayoutResponse.Data data) {
        if (data == null || payout.getStatus() != VendorPayoutStatus.PROCESSING) {
            return Mono.just(payout);
        }
        String status = data.getStatus();
        payout.setGatewayStatus(status);
        payout.setUpdatedAt(LocalDateTime.now());
        if ("SettlementCompleted".equalsIgnoreCase(status)) {
            payout.setStatus(VendorPayoutStatus.SUCCESS);
            payout.setUtrNumber(data.getUtrNumber());
            payout.setSettledAt(LocalDateTime.now());
        } else if ("Declined".equalsIgnoreCase(status)) {
            payout.setStatus(VendorPayoutStatus.FAILED);
            payout.setGatewayDescription(data.getRemark());
        }
        // Anything else (e.g. still "Processing") isn't a terminal transition — stays PROCESSING.
        return vendorPayoutRepository.save(payout);
    }

    private String generateInvoiceNumber() {
        return "PO" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private VendorPayoutResponseDto toDto(VendorPayout p) {
        return VendorPayoutResponseDto.builder()
                .payoutId(p.getId())
                .vendorId(p.getVendorId())
                .amount(p.getAmount())
                .payoutMode(p.getPayoutMode())
                .accountNo(p.getAccountNo())
                .ifscBankCode(p.getIfscBankCode())
                .payeeVpa(p.getPayeeVpa())
                .status(p.getStatus())
                .gatewayStatus(p.getGatewayStatus())
                .gatewayDescription(p.getGatewayDescription())
                .utrNumber(p.getUtrNumber())
                .rejectionReason(p.getRejectionReason())
                .requestedAt(p.getRequestedAt())
                .processedAt(p.getProcessedAt())
                .settledAt(p.getSettledAt())
                .initiatedByRole(p.getInitiatedByRole())
                .initiatedByUserId(p.getInitiatedByUserId())
                .build();
    }
}
