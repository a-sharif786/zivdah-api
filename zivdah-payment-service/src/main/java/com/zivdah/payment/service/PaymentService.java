package com.zivdah.payment.service;

import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface PaymentService {
    Mono<PaymentResponseDto> initiatePayment(PaymentRequestDto dto);
    Mono<PaymentResponseDto> getPayment(Long paymentId);
    Flux<PaymentResponseDto> getPaymentsByOrder(Long orderId);
    Mono<PaymentResponseDto> markPaymentSuccess(Long paymentId);
    Mono<PaymentResponseDto> markPaymentFailed(Long paymentId);
    Mono<Boolean> processPayment(Long orderId, BigDecimal amount);
}
