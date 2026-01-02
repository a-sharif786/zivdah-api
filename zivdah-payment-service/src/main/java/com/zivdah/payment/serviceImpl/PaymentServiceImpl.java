package com.zivdah.payment.serviceImpl;

import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.repository.PaymentRepository;
import com.zivdah.payment.service.PaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    @Autowired
    private PaymentRepository paymentRepository;
    @Override
    public PaymentResponseDto initiatePayment(PaymentRequestDto dto) {
        Payment payment = Payment.builder()
                .orderId(dto.getOrderId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .method(dto.getMethod())
                .transactionId(UUID.randomUUID().toString())
                .build();

        return mapToResponse(paymentRepository.save(payment));
    }

    @Override
    public PaymentResponseDto getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Override
    public List<PaymentResponseDto> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentResponseDto markPaymentSuccess(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setStatus(PaymentStatus.SUCCESS);
        return mapToResponse(paymentRepository.save(payment));
    }

    @Override
    public PaymentResponseDto markPaymentFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setStatus(PaymentStatus.FAILED);
        return mapToResponse(paymentRepository.save(payment));
    }


    @Override
    public boolean processPayment(Long orderId, BigDecimal amount) {
        // Simulate a payment (for demo purposes, random success/failure)
        boolean success = new java.util.Random().nextBoolean();

        // Save the payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .transactionId(UUID.randomUUID().toString())
                .build();

        paymentRepository.save(payment);

        log.info("Payment for order {} processed: {}", orderId, success ? "SUCCESS" : "FAILED");

        return success;
    }


    private PaymentResponseDto mapToResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }

}
