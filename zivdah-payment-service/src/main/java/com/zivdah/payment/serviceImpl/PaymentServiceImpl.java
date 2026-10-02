package com.zivdah.payment.serviceImpl;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.payment.client.OrderServiceClient;
import com.zivdah.payment.dto.DailyAmountDto;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.dto.PaymentStatsResponseDto;
import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentMethod;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayClient;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayException;
import com.zivdah.payment.gateway.ecomworldpay.TransactionNotYetAvailableException;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayTransactionDto;
import com.zivdah.payment.gateway.ecomworldpay.dto.QrIntentRequest;
import com.zivdah.payment.kafka.PaymentKafkaProducer;
import com.zivdah.payment.repository.DailyNetProjection;
import com.zivdah.payment.repository.PaymentRepository;
import com.zivdah.payment.repository.PaymentStatsRepository;
import com.zivdah.payment.repository.PaymentTotalsProjection;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentStatsRepository paymentStatsRepository;
    private final PaymentKafkaProducer paymentKafkaProducer;
    private final OrderServiceClient orderServiceClient;
    private final EcomWorldPayClient ecomWorldPayClient;

    // Same one-paisa allowance order-service uses between the storefront's JS-computed total and
    // its own BigDecimal one (see OrderServiceImpl#TOTAL_TOLERANCE) — the payment amount is that
    // same client-computed grand total.
    private static final BigDecimal LINK_AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private static boolean isTerminal(PaymentStatus s) {
        return s == PaymentStatus.SUCCESS || s == PaymentStatus.FAILED
                || s == PaymentStatus.REFUNDED || s == PaymentStatus.CANCELLED;
    }

    // Callers see only their own payments; ADMIN and internal services see any. A payment that
    // isn't yours looks exactly like one that doesn't exist, so ids can't be probed.
    private Mono<Payment> requireAccess(Payment p, Long currentUserId, boolean privileged) {
        if (privileged || (currentUserId != null && currentUserId.equals(p.getUserId()))) {
            return Mono.just(p);
        }
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + p.getId()));
    }

    @Override
    public Mono<PaymentResponseDto> initiatePayment(PaymentRequestDto dto, Long currentUserId) {
        if (isBlank(dto.getCheckoutRef())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkoutRef is required"));
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }
        // The payment always belongs to the authenticated caller (the body's userId used to be
        // trusted), and is never tied to an order here — only linkOrder() attaches one, after
        // checking the order's owner, status and total against this payment.
        dto.setUserId(currentUserId);
        dto.setOrderId(null);

        // checkoutRef identifies one checkout attempt end-to-end — looking it up first makes
        // retrying "Place Order" for the same cart idempotent: a FAILED row gets retried in
        // place (new gateway attempt, same row), anything else already in flight or settled
        // (PENDING/PROCESSING/SUCCESS) is returned as-is, and only a genuinely new attempt
        // inserts a new row. See OrderServiceImpl#createOrder for the matching order-side check.
        return paymentRepository.findByCheckoutRef(dto.getCheckoutRef())
                .flatMap(existing -> {
                    if (!currentUserId.equals(existing.getUserId())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate checkout reference"));
                    }
                    return existing.getStatus() == PaymentStatus.FAILED
                            ? retryUpiIntent(existing, dto)
                            : Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> createNewPayment(dto)))
                .map(this::mapToResponse);
    }

    // A fresh checkout attempt — no existing row for this checkoutRef yet.
    private Mono<Payment> createNewPayment(PaymentRequestDto dto) {
        Payment payment = Payment.builder()
                .orderId(dto.getOrderId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .method(dto.getMethod())
                .status(PaymentStatus.PENDING)
                .transactionId(UUID.randomUUID().toString())
                .checkoutRef(dto.getCheckoutRef())
                .createdAt(LocalDateTime.now())
                .build();

        // Only UPI goes through EcomWorldPay's QR intent flow (this integration's whole scope) —
        // every other method keeps the pre-existing behaviour of a bare PENDING record that the
        // caller (COD) or a future gateway integration (CARD/NET_BANKING/...) settles separately.
        //
        // (Removed: three log.warn blocks here and in registerUpiIntent that serialized the whole
        // request/payment — customer name, mobile, email, amount — at WARN on every checkout,
        // under a misleading "payment not found" message, into the centralized log store.)
        if (dto.getMethod() != PaymentMethod.UPI) {
            return paymentRepository.save(payment);
        }

        if (isBlank(dto.getFirstName()) || isBlank(dto.getLastName())
                || isBlank(dto.getMobile()) || isBlank(dto.getEmail())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "firstName, lastName, mobile and email are required for UPI payments"));
        }
        return paymentRepository.save(payment)
                .flatMap(saved -> registerUpiIntent(saved, dto));
    }

    // checkoutRef matched a row that previously FAILED — retry the gateway call in place rather
    // than inserting a second row. amount/currency may be stale if the cart changed without the
    // checkout attempt resetting (e.g. a coupon applied between attempts); refresh them from the
    // incoming request first, since registerUpiIntent builds the gateway request from the stored
    // row, not the DTO — but ONLY while the payment isn't linked to an order yet. Once linked, its
    // amount was already verified against that order's server-computed total (see linkOrder), and
    // letting a retry overwrite it would let a customer re-price an existing order to ₹1.
    private Mono<Payment> retryUpiIntent(Payment existing, PaymentRequestDto dto) {
        if (existing.getOrderId() == null) {
            existing.setAmount(dto.getAmount());
            existing.setCurrency(dto.getCurrency());
        }
        return registerUpiIntent(existing, dto);
    }

    // Attaches the order to the payment — the step that makes a later gateway SUCCESS mark that
    // order PAID, so it's where the amount has to be proven. Previously any caller could link any
    // payment to any order, with no check that the amounts matched: pay ₹1, link it to a ₹3000
    // order, and the order went PAID. Now the payment must be the caller's own, the order (read
    // from order-service) must also be theirs and still awaiting payment, and the payment amount
    // must equal the order's server-computed total.
    @Override
    public Mono<PaymentResponseDto> linkOrder(Long paymentId, Long orderId, Long currentUserId) {
        if (orderId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required"));
        }
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> requireAccess(p, currentUserId, false))
                .flatMap(p -> {
                    if (p.getOrderId() != null && !p.getOrderId().equals(orderId)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Payment " + paymentId + " is already linked to a different order"));
                    }
                    if (orderId.equals(p.getOrderId())) {
                        return Mono.just(p); // already linked — idempotent no-op
                    }
                    if (p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED
                            || p.getStatus() == PaymentStatus.CANCELLED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Payment " + paymentId + " can no longer be linked to an order"));
                    }
                    return orderServiceClient.getOrder(orderId)
                            .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> {
                                log.warn("Order lookup for linking payment {} to order {} failed: {}",
                                        paymentId, orderId, ex.getMessage());
                                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
                            })
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId)))
                            .flatMap(order -> {
                                if (!currentUserId.equals(order.getUserId())) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
                                }
                                if (!"CREATED".equals(order.getStatus()) && !"PAYMENT_PENDING".equals(order.getStatus())) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                            "Order " + orderId + " is not awaiting payment"));
                                }
                                if (order.getTotalAmount() == null || p.getAmount() == null
                                        || p.getAmount().subtract(order.getTotalAmount()).abs().compareTo(LINK_AMOUNT_TOLERANCE) > 0) {
                                    log.warn("Payment {} amount {} does not match order {} total {}",
                                            paymentId, p.getAmount(), orderId, order.getTotalAmount());
                                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                            "Payment amount does not match the order total"));
                                }
                                p.setOrderId(orderId);
                                p.setUpdatedAt(LocalDateTime.now());
                                return paymentRepository.save(p);
                            });
                })
                .map(this::mapToResponse);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // Registers the UPI intent with EcomWorldPay and persists the outcome. "saved" is already a
    // row in the DB (PENDING) — this only ever moves it to PROCESSING (intent created, awaiting
    // the customer's UPI app) or FAILED (intent creation itself failed); the actual payment
    // result comes later via handleGatewayCallback / refreshGatewayStatus.
    private Mono<Payment> registerUpiIntent(Payment saved, PaymentRequestDto dto) {
        QrIntentRequest request = QrIntentRequest.builder()
                .mid(ecomWorldPayClient.getMerchantId())
                .amount(saved.getAmount().toPlainString())
                .invno(saved.getTransactionId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .mobile(dto.getMobile())
                .currency(saved.getCurrency() != null ? saved.getCurrency() : "INR")
                .email(dto.getEmail())
                .build();

        return ecomWorldPayClient.createUpiIntent(request)
                .flatMap(response -> {
                    boolean created = response.getStatus() != null && response.getStatus().equalsIgnoreCase("SUCCESS");
                    saved.setGatewayTxnId(response.getTransactionId());
                    saved.setUpiIntent(response.getIntent());
                    saved.setGatewayName("ECOMWORLDPAY");
                    saved.setStatus(created ? PaymentStatus.PROCESSING : PaymentStatus.FAILED);
                    if (!created) {
                        saved.setFailureReason(response.getMessage() != null
                                ? response.getMessage() : "EcomWorldPay declined UPI intent creation");
                    }
                    saved.setUpdatedAt(LocalDateTime.now());

                    return paymentRepository.save(saved);
                })
                .onErrorResume(ex -> {
                    saved.setStatus(PaymentStatus.FAILED);
                    // EcomWorldPayException's message is the gateway's own error text (see
                    // EcomWorldPayClient) — shown to the customer as-is. Any other exception here
                    // is a genuine connectivity/unexpected failure, not something the gateway told
                    // us, so it keeps a prefix for context.
                    saved.setFailureReason(ex instanceof EcomWorldPayException
                            ? ex.getMessage() : "UPI intent creation error: " + ex.getMessage());
                    saved.setUpdatedAt(LocalDateTime.now());
                    return paymentRepository.save(saved);
                });
    }


    @Override
    public Mono<PaymentResponseDto> getPayment(Long paymentId, Long currentUserId, boolean privileged) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                // access is checked BEFORE the gateway refresh, so a stranger can't even trigger one
                .flatMap(p -> requireAccess(p, currentUserId, privileged))
                .flatMap(this::refreshIfAwaitingGateway)
                .map(this::mapToResponse);
    }

    private Mono<Payment> refreshIfAwaitingGateway(Payment p) {
        if (p.getMethod() != PaymentMethod.UPI || p.getStatus() != PaymentStatus.PROCESSING || isBlank(p.getGatewayTxnId())) {
            return Mono.just(p);
        }
        return ecomWorldPayClient.checkTransactionStatus(p.getGatewayTxnId())
                .flatMap(result -> applyGatewayResult(p, result))
                .onErrorResume(TransactionNotYetAvailableException.class, ex -> Mono.just(p))
                .onErrorResume(ex -> {
                    // A plain payment lookup must not fail just because the gateway call hiccuped —
                    // the caller gets the last-known (still PROCESSING) state and tries again later.
                    log.warn("Background gateway status refresh failed for payment {}: {}", p.getId(), ex.getMessage());
                    return Mono.just(p);
                });
    }

    // Internal callers (order-service verifying a Kafka event, chat-service) and ADMIN see every
    // payment on the order; anyone else only the ones that are their own.
    @Override
    public Flux<PaymentResponseDto> getPaymentsByOrder(Long orderId, Long currentUserId, boolean privileged) {
        return paymentRepository.findByOrderId(orderId)
                .filter(p -> privileged || (currentUserId != null && currentUserId.equals(p.getUserId())))
                .map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentResponseDto> markPaymentSuccess(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(this::transitionToSuccess)
                .map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentResponseDto> markPaymentFailed(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> transitionToFailed(p, null))
                .map(this::mapToResponse);
    }

    // Shared by the manual admin/COD mark-success endpoint AND the EcomWorldPay gateway callback
    // / status-check flow — both need identical downstream effects (Kafka event + order-service sync).
    private Mono<Payment> transitionToSuccess(Payment p) {
        p.setStatus(PaymentStatus.SUCCESS);
        p.setPaidAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(p)
                .flatMap(saved -> {
                    paymentKafkaProducer.publishPaymentCompleted(
                            PaymentCompletedEvent.builder()
                                    .orderId(saved.getOrderId()).userId(saved.getUserId()).status("PAID").build());
                    // Synchronous, in-request update so the order's status is correct immediately —
                    // does not depend on the Kafka event above ever being consumed. Carries payment
                    // method/transaction/paidAt along so order-service can generate an invoice
                    // without calling back into payment-service for them (see OrderServiceClient).
                    return orderServiceClient.updatePaymentStatus(
                            saved.getOrderId(), "PAID",
                            saved.getMethod() != null ? saved.getMethod().name() : null,
                            saved.getTransactionId(), saved.getPaidAt()
                    ).thenReturn(saved);
                });
    }

    private Mono<Payment> transitionToFailed(Payment p, String failureReason) {
        p.setStatus(PaymentStatus.FAILED);
        if (failureReason != null) {
            p.setFailureReason(failureReason);
        }
        p.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(p)
                .flatMap(saved -> {
                    paymentKafkaProducer.publishPaymentCompleted(
                            PaymentCompletedEvent.builder()
                                    .orderId(saved.getOrderId()).userId(saved.getUserId()).status("FAILED").build());
                    return orderServiceClient.updatePaymentStatus(saved.getOrderId(), "CANCELLED").thenReturn(saved);
                });
    }

    // The callback endpoint is necessarily unauthenticated (EcomWorldPay pushes it with no
    // credentials and no signature), so its body is NEVER applied: previously anyone could POST
    // {"invoiceNumber": "<a payment's transactionId>", "status": "SUCCESS"} and the order went
    // PAID. The callback is now only a signal that the payment may have resolved — its real
    // outcome is pulled from EcomWorldPay's authenticated Transaction Status API (the same call the
    // storefront's polling and "check now" use), and only THAT result is applied, after the
    // amount/identity checks in applyGatewayResult. Always completes (never errors), so the
    // controller still acks 200 and the gateway doesn't retry endlessly.
    @Override
    public Mono<Void> handleGatewayCallback(EcomWorldPayTransactionDto callback) {
        if (callback == null || isBlank(callback.getInvoiceNumber())) {
            log.warn("EcomWorldPay callback with no invoiceNumber — ignoring");
            return Mono.empty();
        }
        return paymentRepository.findByTransactionId(callback.getInvoiceNumber())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("EcomWorldPay callback for unknown invoiceNumber {} (pgTxnId {})",
                            callback.getInvoiceNumber(), callback.getPgTxnId());
                    return Mono.empty();
                }))
                .filter(p -> !isTerminal(p.getStatus()))
                .flatMap(p -> {
                    if (isBlank(p.getGatewayTxnId())) {
                        // No gateway transaction was ever registered for this payment (QR creation
                        // failed), so there's nothing to verify the callback against.
                        log.warn("EcomWorldPay callback for payment {} with no gateway transaction — ignoring", p.getId());
                        return Mono.empty();
                    }
                    return ecomWorldPayClient.checkTransactionStatus(p.getGatewayTxnId())
                            .flatMap(verified -> applyGatewayResult(p, verified))
                            .onErrorResume(TransactionNotYetAvailableException.class, ex -> Mono.just(p))
                            .onErrorResume(ex -> {
                                log.warn("Could not verify EcomWorldPay callback for payment {}: {}", p.getId(), ex.getMessage());
                                return Mono.just(p);
                            });
                })
                .then();
    }

    @Override
    public Mono<PaymentResponseDto> refreshGatewayStatus(Long paymentId, Long currentUserId, boolean privileged) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> requireAccess(p, currentUserId, privileged))
                .flatMap(p -> {
                    if (isBlank(p.getGatewayTxnId())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Payment " + paymentId + " has no associated gateway transaction to check"));
                    }
                    return ecomWorldPayClient.checkTransactionStatus(p.getGatewayTxnId())
                            .flatMap(result -> applyGatewayResult(p, result))
                            // The customer's UPI payment is still in flight at EcomWorldPay/NPCI — not a
                            // decline, just "ask again later". Leave the payment PROCESSING as-is so the
                            // next poll/click can still resolve it, instead of surfacing this as an error.
                            .onErrorResume(TransactionNotYetAvailableException.class, ex -> Mono.just(p));
                })
                .map(this::mapToResponse);
    }

    // Applies a callback/status-check result to a payment that's still awaiting the customer's
    // action (PENDING/PROCESSING). Idempotent: a payment already in a terminal state is returned
    // as-is — EcomWorldPay's callback can be retried, and the status-check API can be polled
    // repeatedly, so this must not double-publish the Kafka event or re-sync order-service.
    //
    // result's fields are only trusted (and only overwrite the payment's own) when non-blank — an
    // inconclusive gateway answer must never blank out a gatewayTxnId/response that a prior, good
    // answer already recorded. Similarly, a null/unrecognized status is left PROCESSING rather than
    // treated as a decline: EcomWorldPay's flat DTO shape is only sent for a truly resolved outcome
    // (see EcomWorldPayClient#checkTransactionStatus for the "not resolved yet" case), so reaching
    // here with neither SUCCESS nor a real status is unexpected and safest treated as "try again".
    //
    // A SUCCESS is only accepted if it's provably for THIS payment and the FULL amount — the
    // gateway's reported amount must equal ours exactly, and its invoiceNumber / pgTxnId (when it
    // reports them) must match ours. Previously the amount was never compared at all.
    private Mono<Payment> applyGatewayResult(Payment p, EcomWorldPayTransactionDto result) {
        if (isTerminal(p.getStatus())) {
            return Mono.just(p);
        }

        boolean reportedSuccess = result.getStatus() != null && result.getStatus().equalsIgnoreCase("SUCCESS");
        // Checked against the payment's identifiers as they were BEFORE this result is merged in.
        String mismatch = reportedSuccess ? successMismatch(p, result) : null;
        if (reportedSuccess && result.getAmount() == null) {
            // Can't prove the amount — fail closed: don't settle, stay PROCESSING for a later poll.
            log.error("EcomWorldPay reported SUCCESS for payment {} without an amount — not settling", p.getId());
            return Mono.just(p);
        }

        if (!isBlank(result.getPgTxnId())) {
            p.setGatewayTxnId(result.getPgTxnId());
        }
        if (!isBlank(result.getPayerVPA())) {
            p.setPayerVpa(result.getPayerVPA());
        }
        if (!isBlank(result.getRrn())) {
            p.setRrn(result.getRrn());
        }
        if (!isBlank(result.getNpciTxnId())) {
            p.setNpciTxnId(result.getNpciTxnId());
        }
        p.setGatewayName("ECOMWORLDPAY");
        if (!isBlank(result.getResponseMessage())) {
            p.setGatewayResponse(result.getResponseMessage());
        }

        if (result.getStatus() == null) {
            return Mono.just(p); // unresolved/unrecognized — stay PROCESSING, don't guess
        }
        if (mismatch != null) {
            log.error("EcomWorldPay SUCCESS for payment {} failed verification ({}) — flagged for review",
                    p.getId(), mismatch);
            return transitionToFailed(p,
                    "Payment could not be verified. If money was debited, it will be reviewed and refunded.");
        }
        return reportedSuccess ? transitionToSuccess(p) : transitionToFailed(p, result.getResponseMessage());
    }

    // null when the gateway's SUCCESS matches this payment; otherwise a short (log-only) reason.
    private static String successMismatch(Payment p, EcomWorldPayTransactionDto result) {
        if (result.getAmount() != null && (p.getAmount() == null || result.getAmount().compareTo(p.getAmount()) != 0)) {
            return "amount " + result.getAmount() + " != expected " + p.getAmount();
        }
        if (!isBlank(result.getInvoiceNumber()) && !result.getInvoiceNumber().equals(p.getTransactionId())) {
            return "invoiceNumber mismatch";
        }
        if (!isBlank(result.getPgTxnId()) && !isBlank(p.getGatewayTxnId()) && !result.getPgTxnId().equals(p.getGatewayTxnId())) {
            return "pgTxnId mismatch";
        }
        return null;
    }

    @Override
    public Mono<PaymentResponseDto> refundPayment(Long paymentId, BigDecimal amount) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> {
                    if (p.getStatus() != PaymentStatus.SUCCESS && p.getStatus() != PaymentStatus.REFUNDED) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Only a successfully paid payment can be refunded"));
                    }
                    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refund amount must be positive"));
                    }
                    BigDecimal remaining = remainingRefundable(p);
                    if (amount.compareTo(remaining) > 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Refund amount exceeds the remaining refundable balance of " + remaining));
                    }
                    return doRefund(p, amount);
                })
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Void> refundByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED)
                .next()
                .flatMap(p -> {
                    BigDecimal remaining = remainingRefundable(p);
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.empty(); // already fully refunded — idempotent no-op
                    }
                    return doRefund(p, remaining);
                })
                .then();
    }

    private BigDecimal remainingRefundable(Payment p) {
        BigDecimal alreadyRefunded = p.getRefundAmount() != null ? p.getRefundAmount() : BigDecimal.ZERO;
        return p.getAmount().subtract(alreadyRefunded);
    }

    // Persists the refund and, only once the payment is FULLY refunded, syncs the order to
    // REFUNDED. A partial refund updates payment-service's own numbers immediately but doesn't
    // push the order — there's no partial-refund order status, so only a full refund flips it.
    private Mono<Payment> doRefund(Payment p, BigDecimal refundAmount) {
        BigDecimal alreadyRefunded = p.getRefundAmount() != null ? p.getRefundAmount() : BigDecimal.ZERO;
        BigDecimal newTotal = alreadyRefunded.add(refundAmount);
        p.setRefundAmount(newTotal);
        p.setRefundedAt(LocalDateTime.now());
        p.setStatus(PaymentStatus.REFUNDED);
        p.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(p)
                .flatMap(saved -> {
                    boolean fullyRefunded = newTotal.compareTo(saved.getAmount()) >= 0;
                    Mono<Void> syncOrder = fullyRefunded
                            ? orderServiceClient.updatePaymentStatus(saved.getOrderId(), "REFUNDED")
                            : Mono.empty();
                    return syncOrder.thenReturn(saved);
                });
    }

    @Override
    public Flux<PaymentResponseDto> getAllPayments(Pageable pageable, PaymentStatus status) {
        Flux<Payment> payments = status != null
                ? paymentRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : paymentRepository.findAllByOrderByCreatedAtDesc(pageable);
        return payments.map(this::mapToResponse);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Override
    public Mono<PaymentStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to) {

        Mono<PaymentTotalsProjection> allTime = paymentStatsRepository.sumTotalsAllTime();
        Mono<PaymentTotalsProjection> inRange = paymentStatsRepository.sumTotalsInRange(from, to);
        Mono<List<DailyNetProjection>> dailySeries = paymentStatsRepository.dailyNetSeries(from, to).collectList();

        return Mono.zip(allTime, inRange, dailySeries)
                .map(t -> {
                    PaymentTotalsProjection allTimeTotals = t.getT1();
                    PaymentTotalsProjection rangeTotals = t.getT2();

                    // SQL side already does COALESCE(SUM(...), 0) (see PaymentStatsRepository) —
                    // orZero is just a last line of defense, not load-bearing.
                    BigDecimal allTimeGross = orZero(allTimeTotals.getGross());
                    BigDecimal allTimeRefunded = orZero(allTimeTotals.getRefunded());
                    BigDecimal rangeGross = orZero(rangeTotals.getGross());
                    BigDecimal rangeRefunded = orZero(rangeTotals.getRefunded());

                    BigDecimal totalReceivedAllTime = allTimeGross.subtract(allTimeRefunded);
                    BigDecimal totalRefundedAllTime = allTimeRefunded;
                    BigDecimal totalReceivedInRange = rangeGross.subtract(rangeRefunded);
                    BigDecimal totalRefundedInRange = rangeRefunded;

                    List<DailyAmountDto> series = t.getT3().stream()
                            .map(d -> DailyAmountDto.builder().date(d.getDay()).amount(orZero(d.getAmount())).build())
                            .collect(Collectors.toList());

                    return PaymentStatsResponseDto.builder()
                            .totalReceivedAllTime(totalReceivedAllTime)
                            .totalReceivedInRange(totalReceivedInRange)
                            .totalRefundedAllTime(totalRefundedAllTime)
                            .totalRefundedInRange(totalRefundedInRange)
                            .series(series)
                            .build();
                });
    }

    private PaymentResponseDto mapToResponse(Payment p) {
        return PaymentResponseDto.builder()
                .paymentId(p.getId()).orderId(p.getOrderId()).userId(p.getUserId())
                .amount(p.getAmount()).method(p.getMethod()).status(p.getStatus())
                .transactionId(p.getTransactionId()).createdAt(p.getCreatedAt())
                .refundAmount(p.getRefundAmount()).refundedAt(p.getRefundedAt())
                .failureReason(p.getFailureReason())
                .upiIntent(p.getUpiIntent()).gatewayTxnId(p.getGatewayTxnId())
                .payerVpa(p.getPayerVpa()).rrn(p.getRrn()).npciTxnId(p.getNpciTxnId())
                .build();
    }
}
