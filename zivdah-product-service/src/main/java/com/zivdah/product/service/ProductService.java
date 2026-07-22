package com.zivdah.product.service;

import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductService {
    Mono<ProductResponseDto> createProduct(ProductRequestDto dto);
    Mono<ProductResponseDto> getProductById(Long id);
    Flux<ProductResponseDto> getAllProducts(Pageable pageable);
    Flux<ProductResponseDto> getAllWishlist(Pageable pageable);
    Mono<ProductResponseDto> updateProduct(Long id, ProductRequestDto dto);
    Mono<Void> deleteProduct(Long id);
    Mono<List<ProductCategory>> getAllCategories();
    Mono<ProductResponseDto> updateWishlist(Long id, Boolean fav);
}
