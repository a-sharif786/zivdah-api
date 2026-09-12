package com.zivdah.product.repository;

import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveCrudRepository<ProductEntity, Long> {
    // OrderByCreatedAtDesc: admin/vendor/customer product lists should show new products on top.
    Flux<ProductEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Flux<ProductEntity> findByCategoryOrderByCreatedAtDesc(ProductCategory category, Pageable pageable);
    Flux<ProductEntity> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);
    Flux<ProductEntity> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);
}
