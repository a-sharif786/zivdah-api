package com.zivdah.review.repository;

import com.zivdah.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ReviewRepository extends ReactiveCrudRepository<Review, Long> {
    Flux<Review> findAllBy(Pageable pageable);
    Flux<Review> findByProductId(Long productId);
}
