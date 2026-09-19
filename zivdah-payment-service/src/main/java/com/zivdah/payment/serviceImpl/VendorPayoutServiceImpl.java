package com.zivdah.payment.serviceImpl;

import com.zivdah.payment.client.AuthServiceClient;
import com.zivdah.payment.client.dto.VendorBankDetailsDto;
import com.zivdah.payment.dto.VendorPayoutResponseDto;
import com.zivdah.payment.entity.VendorPayout;
import com.zivdah.payment.enums.VendorPayoutStatus;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayClient;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutRequest;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import com.zivdah.payment.repository.VendorPayoutRepository;
import com.zivdah.payment.service.VendorPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VendorPayoutServiceImpl implements VendorPayoutService {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final AuthServiceClient authServiceClient;
    private final EcomWorldPayClient ecomWorldPayClient;

    @Override
    public Mono<VendorPayoutResponseDto> requestPayout(Long vendorId, BigDecimal amount) {
        return authServiceClient.getVendorBankDetails(vendorId)
                .flatMap(bank -> {
                    if (!bank.hasPayoutDestination()) {
                        return Mono.error(new RuntimeException(
                                "No bank account or UPI VPA on file — update your profile before requesting a payout"));
                    }
                    boolean useUpi = bank.getUpiVpa() != null && !bank.getUpiVpa().isBlank();
                    // Snapshotted here (not re-read from the vendor's profile at approval time) so
                    // a later profile edit can't retroactively change where an already-submitted
                    // request pays out to — whatever the admin approves is exactly what was asked.
                    VendorPayout payout = VendorPayout.builder()
                            .vendorId(vendorId)
                            .amount(amount)
                            .payoutMode(useUpi ? "UPI" : "IMPS")
                            .accountNo(useUpi ? null : bank.getBankAccountNumber())
                            .ifscBankCode(useUpi ? null : bank.getBankIfscCode())
                            .payeeVpa(useUpi ? bank.getUpiVpa() : null)
                            .status(VendorPayoutStatus.REQUESTED)
                            .invoiceNumber(generateInvoiceNumber())
                            .requestedAt(LocalDateTime.now())
                            .build();
                    return vendorPayoutRepository.save(payout);
                })
                .map(this::toDto);
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
        return vendorPayoutRepository.findById(payoutId)
                .switchIfEmpty(Mono.error(new RuntimeException("Payout not found")))
                .flatMap(payout -> {
                    if (payout.getStatus() != VendorPayoutStatus.REQUESTED) {
                        return Mono.error(new RuntimeException("Only requested payouts can be approved"));
                    }
                    return authServiceClient.getVendorBankDetails(payout.getVendorId())
                            .flatMap(vendor -> submitPayout(payout, vendor, ipAddress));
                })
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
                .flatMap(response -> applyCreateResult(payout, response.getData()));
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
                .build();
    }
}
