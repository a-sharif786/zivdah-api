package com.zivdah.order.repository;

import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    Flux<Order> findByUserId(Long userId);
    // OrderByCreatedAtDesc: admin/vendor order lists should show new orders on top.
    Flux<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Flux<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    Flux<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    // Client-generated key for one checkout attempt — see OrderServiceImpl#createOrder's
    // idempotency lookup, backed by a partial unique index (V4 migration).
    Mono<Order> findByIdempotencyKey(String idempotencyKey);
}
