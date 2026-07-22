package com.zivdah.cart.repository;

import com.zivdah.cart.entity.CartItemEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CartRepository extends ReactiveCrudRepository<CartItemEntity, Long> {
    Flux<CartItemEntity> findByUserId(Long userId);
    Mono<Void> deleteByUserId(Long userId);
}
