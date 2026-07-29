package com.zivdah.product.repository;

import com.zivdah.product.entity.WishlistEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WishlistRepository extends ReactiveCrudRepository<WishlistEntity, Long> {
    Flux<WishlistEntity> findByUserId(Long userId, Pageable pageable);
    Mono<WishlistEntity> findByUserIdAndProductId(Long userId, Long productId);
    Mono<Void> deleteByUserIdAndProductId(Long userId, Long productId);
}
