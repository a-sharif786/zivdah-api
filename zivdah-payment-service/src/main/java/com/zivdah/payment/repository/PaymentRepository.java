package com.zivdah.payment.repository;

import com.zivdah.payment.entity.Payment;
import com.zivdah.payment.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
    Flux<Payment> findByOrderId(Long orderId);
    Flux<Payment> findAllBy(Pageable pageable);
    Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
