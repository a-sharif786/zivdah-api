package com.zivdah.payment.repository;

import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
    Flux<Payment> findByOrderId(Long orderId);
    // OrderByCreatedAtDesc: admin payment list should show new payments on top.
    Flux<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Flux<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);
    Flux<Payment> findByStatus(PaymentStatus status);
    Flux<Payment> findByStatusAndPaidAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);

    // transactionId doubles as the invoice number ("invno"/"invoiceNumber") we hand EcomWorldPay
    // at QR-intent creation, so the async transaction callback can be matched back to a payment —
    // see EcomWorldPayClient / PaymentServiceImpl#handleGatewayCallback.
    Mono<Payment> findByTransactionId(String transactionId);

    // Client-generated key for one checkout attempt — see PaymentServiceImpl#initiatePayment's
    // idempotency lookup, backed by a partial unique index (V6 migration).
    Mono<Payment> findByCheckoutRef(String checkoutRef);

    // Aggregate stats queries (totals + daily series) live in PaymentStatsRepository, hand-written
    // against DatabaseClient — see that class for why they aren't plain @Query interface
    // projections here.
}
