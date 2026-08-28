package com.zivdah.product.service;

import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductService {
    Mono<ProductResponseDto> createProduct(ProductRequestDto dto, FilePart image, Long currentUserId, String role);
    Mono<ProductResponseDto> getProductById(Long id);
    Flux<ProductResponseDto> getAllProducts(Pageable pageable);
    Flux<ProductResponseDto> getProductsByCategory(ProductCategory category, Pageable pageable);
    Flux<ProductResponseDto> searchProducts(String keyword, Pageable pageable);
    Flux<ProductResponseDto> getWishlist(Long userId, Pageable pageable);
    Mono<ProductResponseDto> updateProduct(Long id, ProductRequestDto dto, FilePart image, Long currentUserId, String role);
    Mono<Void> deleteProduct(Long id, Long currentUserId, String role);
    Mono<List<ProductCategory>> getAllCategories();
    Mono<ProductResponseDto> updateWishlist(Long userId, Long productId, Boolean fav);
    Flux<ProductResponseDto> getProductsByVendor(Long vendorId, Pageable pageable);
    Mono<Long> countProducts();
}
