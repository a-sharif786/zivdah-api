package com.zivdah.product.service;

import com.zivdah.product.dto.CategoryRequestDto;
import com.zivdah.product.dto.CategoryResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryService {
    Flux<CategoryResponseDto> getCategories();
    Flux<CategoryResponseDto> getAllCategoriesForAdmin();
    Mono<CategoryResponseDto> getCategoryById(Long id);
    Mono<CategoryResponseDto> createCategory(CategoryRequestDto dto);
    Mono<CategoryResponseDto> updateCategory(Long id, CategoryRequestDto dto);
    Mono<Void> deleteCategory(Long id);
    Mono<CategoryResponseDto> toggleActive(Long id);
}
