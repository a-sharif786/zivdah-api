package com.zivdah.payment.repository;

import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collection;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
    Flux<Payment> findByOrderId(Long orderId);
    Flux<Payment> findAllBy(Pageable pageable);
    Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable);
    Flux<Payment> findByStatus(PaymentStatus status);
    Flux<Payment> findByStatusAndPaidAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);
    // SUCCESS + REFUNDED together — "ever successfully paid", so refund amounts can be netted
    // out of it rather than the payment vanishing from the sum once refunded.
    Flux<Payment> findByStatusIn(Collection<PaymentStatus> statuses);
    Flux<Payment> findByStatusInAndPaidAtBetween(Collection<PaymentStatus> statuses, LocalDateTime from, LocalDateTime to);
}
