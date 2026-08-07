package com.zivdah.cart.repository;

import com.zivdah.cart.entity.CartItemEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CartRepository extends ReactiveCrudRepository<CartItemEntity, Long> {
    Flux<CartItemEntity> findByUserId(Long userId);

    @Query("DELETE FROM cart_items WHERE user_id = :userId")
    Mono<Void> deleteByUserId(Long userId);

    @Query("DELETE FROM cart_items WHERE id = :id")
    Mono<Void> deleteCartItemById(Long id);
}
