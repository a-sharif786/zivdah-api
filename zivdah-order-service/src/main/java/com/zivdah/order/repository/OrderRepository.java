package com.zivdah.order.repository;

import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    Flux<Order> findByUserId(Long userId);
    Flux<Order> findAllBy(Pageable pageable);
    Flux<Order> findByStatus(OrderStatus status, Pageable pageable);
}
