package com.zivdah.product.repository;

import com.zivdah.product.entity.Category;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryRepository extends ReactiveCrudRepository<Category, Long> {
    Flux<Category> findByActiveTrue();
    Mono<Boolean> existsBySlug(String slug);
    Mono<Boolean> existsBySlugAndIdNot(String slug, Long id);
}
