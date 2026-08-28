package com.zivdah.payment.serviceImpl;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.payment.client.OrderServiceClient;
import com.zivdah.payment.dto.DailyAmountDto;
import com.zivdah.payment.dto.PaymentRequestDto;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.dto.PaymentStatsResponseDto;
import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.kafka.PaymentKafkaProducer;
import com.zivdah.payment.repository.PaymentRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentKafkaProducer paymentKafkaProducer;
    private final OrderServiceClient orderServiceClient;

    @Override
    public Mono<PaymentResponseDto> initiatePayment(PaymentRequestDto dto) {
        Payment payment = Payment.builder()
                .orderId(dto.getOrderId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .method(dto.getMethod())
                .status(PaymentStatus.PENDING)
                .transactionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
        return paymentRepository.save(payment).map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentResponseDto> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .map(this::mapToResponse);
    }

    @Override
    public Flux<PaymentResponseDto> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId).map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentResponseDto> markPaymentSuccess(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> {
                    p.setStatus(PaymentStatus.SUCCESS);
                    p.setPaidAt(LocalDateTime.now());
                    return paymentRepository.save(p);
                })
                .flatMap(p -> {
                    paymentKafkaProducer.publishPaymentCompleted(
                            PaymentCompletedEvent.builder()
                                    .orderId(p.getOrderId()).userId(p.getUserId()).status("PAID").build());
                    // Synchronous, in-request update so the order's status is correct immediately —
                    // does not depend on the Kafka event above ever being consumed.
                    return orderServiceClient.updatePaymentStatus(p.getOrderId(), "PAID").thenReturn(p);
                })
                .map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentResponseDto> markPaymentFailed(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId)))
                .flatMap(p -> {
                    p.setStatus(PaymentStatus.FAILED);
                    return paymentRepository.save(p);
                })
                .flatMap(p -> {
                    paymentKafkaProducer.publishPaymentCompleted(
                            PaymentCompletedEvent.builder()
                                    .orderId(p.getOrderId()).userId(p.getUserId()).status("FAILED").build());
                    return orderServiceClient.updatePaymentStatus(p.getOrderId(), "CANCELLED").thenReturn(p);
                })
                .map(this::mapToResponse);
    }

    @Override
    public Mono<Boolean> processPayment(Long orderId, BigDecimal amount) {
        boolean success = new java.util.Random().nextBoolean();
        Payment payment = Payment.builder()
                .orderId(orderId).amount(amount)
                .status(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .transactionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
        return paymentRepository.save(payment)
                .doOnSuccess(p -> log.info("Payment for order {} processed: {}", orderId, success ? "SUCCESS" : "FAILED"))
                .map(p -> success);
    }

    @Override
    public Flux<PaymentResponseDto> getAllPayments(Pageable pageable, PaymentStatus status) {
        Flux<Payment> payments = status != null
                ? paymentRepository.findByStatus(status, pageable)
                : paymentRepository.findAllBy(pageable);
        return payments.map(this::mapToResponse);
    }

    @Override
    public Mono<PaymentStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to) {

        Mono<BigDecimal> totalReceivedAllTime = paymentRepository.findByStatus(PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<List<Payment>> receivedInRange = paymentRepository
                .findByStatusAndPaidAtBetween(PaymentStatus.SUCCESS, from, to)
                .collectList();

        return Mono.zip(totalReceivedAllTime, receivedInRange)
                .map(t -> {
                    List<Payment> payments = t.getT2();

                    BigDecimal totalReceivedInRange = payments.stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Bucket by calendar day (paidAt), summing amounts, sorted ascending.
                    Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
                    for (Payment p : payments) {
                        LocalDate day = p.getPaidAt().toLocalDate();
                        byDay.merge(day, p.getAmount(), BigDecimal::add);
                    }

                    List<DailyAmountDto> series = byDay.entrySet().stream()
                            .map(e -> DailyAmountDto.builder().date(e.getKey()).amount(e.getValue()).build())
                            .sorted(Comparator.comparing(DailyAmountDto::getDate))
                            .collect(Collectors.toList());

                    return PaymentStatsResponseDto.builder()
                            .totalReceivedAllTime(t.getT1())
                            .totalReceivedInRange(totalReceivedInRange)
                            .series(series)
                            .build();
                });
    }

    private PaymentResponseDto mapToResponse(Payment p) {
        return PaymentResponseDto.builder()
                .paymentId(p.getId()).orderId(p.getOrderId()).userId(p.getUserId())
                .amount(p.getAmount()).method(p.getMethod()).status(p.getStatus())
                .transactionId(p.getTransactionId()).createdAt(p.getCreatedAt())
                .build();
    }
}
