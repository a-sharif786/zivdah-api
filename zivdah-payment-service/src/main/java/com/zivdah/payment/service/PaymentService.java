package com.zivdah.payment.service;

import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.dto.PaymentStatsResponseDto;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayTransactionDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// currentUserId is always the authenticated caller (from the JWT, never the request body).
// privileged = ADMIN or an internal service — may see any payment; everyone else only their own.
public interface PaymentService {
    // The payment always belongs to currentUserId; dto.userId/orderId are ignored (the order is
    // attached later by linkOrder, which verifies it).
    Mono<PaymentResponseDto> initiatePayment(PaymentRequestDto dto, Long currentUserId);
    // Attaches a real orderId to a payment intent that was created before the order existed (see
    // initiatePayment). The payment and the order must both belong to currentUserId, the order
    // must still be awaiting payment, and the payment amount must equal the order's total.
    // Idempotent no-op if already linked to this same orderId; 409 if linked to a different one.
    Mono<PaymentResponseDto> linkOrder(Long paymentId, Long orderId, Long currentUserId);
    Mono<PaymentResponseDto> getPayment(Long paymentId, Long currentUserId, boolean privileged);
    Flux<PaymentResponseDto> getPaymentsByOrder(Long orderId, Long currentUserId, boolean privileged);
    // ADMIN-only manual overrides (see PaymentController) — e.g. COD cash collected.
    Mono<PaymentResponseDto> markPaymentSuccess(Long paymentId);
    Mono<PaymentResponseDto> markPaymentFailed(Long paymentId);
    Mono<PaymentResponseDto> refundPayment(Long paymentId, BigDecimal amount);
    // Internal, order-service-only sync: fully refunds the order's payment. No-op if there is
    // no refundable payment for this order (nothing found, or already fully refunded).
    Mono<Void> refundByOrder(Long orderId);
    Flux<PaymentResponseDto> getAllPayments(Pageable pageable, PaymentStatus status);
    Mono<PaymentStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to);

    // EcomWorldPay UPI QR (PayIn) — see gateway.ecomworldpay package.
    // Async push from the gateway (no user JWT — see SecurityConfig), matched to a payment via
    // callback.getInvoiceNumber() == Payment.transactionId. Treated only as a signal: the real
    // outcome is pulled from the gateway's status API before anything is applied.
    Mono<Void> handleGatewayCallback(EcomWorldPayTransactionDto callback);
    // Active poll against the gateway's status-check API, for when a callback is missed/delayed.
    Mono<PaymentResponseDto> refreshGatewayStatus(Long paymentId, Long currentUserId, boolean privileged);
}
