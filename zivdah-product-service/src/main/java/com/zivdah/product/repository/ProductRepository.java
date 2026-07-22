package com.zivdah.product.repository;

import com.zivdah.product.entity.ProductEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveCrudRepository<ProductEntity, Long> {
    Flux<ProductEntity> findAllBy(Pageable pageable);
    Flux<ProductEntity> findByFavTrue(Pageable pageable);
}
