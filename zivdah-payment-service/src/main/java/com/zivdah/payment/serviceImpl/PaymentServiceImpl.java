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

    // "Ever successfully paid" — a refunded payment stays in this set (only its net amount
    // drops), so refunds are netted out below rather than the payment simply vanishing from
    // the sum.
    private static final List<PaymentStatus> RECEIVED_STATUSES = List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    private static BigDecimal refundOf(Payment p) {
        return p.getRefundAmount() != null ? p.getRefundAmount() : BigDecimal.ZERO;
    }

    private static BigDecimal netOf(Payment p) {
        return p.getAmount().subtract(refundOf(p));
    }

    @Override
    public Mono<PaymentStatsResponseDto> getStats(LocalDateTime from, LocalDateTime to) {

        Mono<List<Payment>> allTime = paymentRepository.findByStatusIn(RECEIVED_STATUSES).collectList();

        Mono<List<Payment>> inRange = paymentRepository
                .findByStatusInAndPaidAtBetween(RECEIVED_STATUSES, from, to)
                .collectList();

        return Mono.zip(allTime, inRange)
                .map(t -> {
                    List<Payment> allTimePayments = t.getT1();
                    List<Payment> rangePayments = t.getT2();

                    BigDecimal totalReceivedAllTime = allTimePayments.stream()
                            .map(PaymentServiceImpl::netOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalRefundedAllTime = allTimePayments.stream()
                            .map(PaymentServiceImpl::refundOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalReceivedInRange = rangePayments.stream()
                            .map(PaymentServiceImpl::netOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalRefundedInRange = rangePayments.stream()
                            .map(PaymentServiceImpl::refundOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Bucket by calendar day (paidAt), summing net (amount - refundAmount), sorted ascending.
                    Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
                    for (Payment p : rangePayments) {
                        LocalDate day = p.getPaidAt().toLocalDate();
                        byDay.merge(day, netOf(p), BigDecimal::add);
                    }

                    List<DailyAmountDto> series = byDay.entrySet().stream()
                            .map(e -> DailyAmountDto.builder().date(e.getKey()).amount(e.getValue()).build())
                            .sorted(Comparator.comparing(DailyAmountDto::getDate))
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
                .build();
    }
}
