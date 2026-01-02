package com.zivdah.product.service;


import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public interface ProductService {

    ProductResponseDto createProduct(ProductRequestDto dto);

    ProductResponseDto getProductById(Long id);

    List<ProductResponseDto> getAllProducts(Pageable pageable);

    List<ProductResponseDto> getAllWishlist(Pageable pageable);

    ProductResponseDto updateProduct(Long id, ProductRequestDto dto);

    void deleteProduct(Long id);
    // ✅ NEW
    List<ProductCategory> getAllCategories();

    ProductResponseDto updateWishlist(Long id, Boolean fav);
}
