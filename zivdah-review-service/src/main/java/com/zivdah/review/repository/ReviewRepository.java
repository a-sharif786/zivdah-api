package com.zivdah.review.repository;

import com.zivdah.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ReviewRepository extends ReactiveCrudRepository<Review, Long> {
    // OrderByCreatedAtDesc: review lists (admin, and per-product on vendor/web) show new reviews on top.
    Flux<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Flux<Review> findByProductId(Long productId);
    Flux<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
}
