package com.zivdah.payment.service;

import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
@Service
public interface PaymentService {
    PaymentResponseDto initiatePayment(PaymentRequestDto dto);

    PaymentResponseDto getPayment(Long paymentId);

    List<PaymentResponseDto> getPaymentsByOrder(Long orderId);

    PaymentResponseDto markPaymentSuccess(Long paymentId);

    PaymentResponseDto markPaymentFailed(Long paymentId);

    // Optional for Kafka consumer
    boolean processPayment(Long orderId, BigDecimal amount);
}
