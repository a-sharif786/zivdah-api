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

public interface PaymentService {
    Mono<PaymentResponseDto> initiatePayment(PaymentRequestDto dto);
    Mono<PaymentResponseDto> getPayment(Long paymentId);
    Flux<PaymentResponseDto> getPaymentsByOrder(Long orderId);
    Mono<PaymentResponseDto> markPaymentSuccess(Long paymentId);
    Mono<PaymentResponseDto> markPaymentFailed(Long paymentId);
    Mono<PaymentResponseDto> refundPayment(Long paymentId, BigDecimal amount);
    // Internal, order-service-only sync: fully refunds the order's payment. No-op if there is
    // no refundable payment for this order (nothing found, or already fully refunded).
    Mono<Void> refundByOrder(Long orderId);
    Mono<Boolean> processPayment(Long orderId, BigDecimal amount);
    Flux<PaymentResponseDto> getAllPayments(Pageable pageable, PaymentStatus status);
    Mono<PaymentStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to);

    // EcomWorldPay UPI QR (PayIn) — see gateway.ecomworldpay package.
    // Async push from the gateway (no user JWT — see SecurityConfig); matched to a payment via
    // callback.getInvoiceNumber() == Payment.transactionId.
    Mono<Void> handleGatewayCallback(EcomWorldPayTransactionDto callback);
    // Active poll against the gateway's status-check API, for when a callback is missed/delayed.
    Mono<PaymentResponseDto> refreshGatewayStatus(Long paymentId);
}
