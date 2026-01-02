package com.zivdah.product.repository;

import com.zivdah.product.entity.ProductEntity;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Page<ProductEntity> findByFavTrue(Pageable pageable);
}
