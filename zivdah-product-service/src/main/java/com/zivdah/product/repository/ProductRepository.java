package com.zivdah.product.repository;

import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveCrudRepository<ProductEntity, Long> {
    Flux<ProductEntity> findAllBy(Pageable pageable);
    Flux<ProductEntity> findByCategory(ProductCategory category, Pageable pageable);
    Flux<ProductEntity> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    Flux<ProductEntity> findByVendorId(Long vendorId, Pageable pageable);
}
