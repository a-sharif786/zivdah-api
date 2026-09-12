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
                    // does not depend on the Kafka event above ever being consumed. Carries payment
                    // method/transaction/paidAt along so order-service can generate an invoice
                    // without calling back into payment-service for them (see OrderServiceClient).
                    return orderServiceClient.updatePaymentStatus(
                            p.getOrderId(), "PAID",
                            p.getMethod() != null ? p.getMethod().name() : null,
                            p.getTransactionId(), p.getPaidAt()
                    ).thenReturn(p);
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
                .build();
    }
}
