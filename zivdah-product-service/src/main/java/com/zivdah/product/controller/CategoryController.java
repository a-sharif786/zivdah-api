package com.zivdah.product.controller;

import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.CategoryRequestDto;
import com.zivdah.product.dto.CategoryResponseDto;
import com.zivdah.product.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/category")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/getAll")
    public Mono<ResponseEntity<ApiResponse<List<CategoryResponseDto>>>> getCategories() {
        return categoryService.getCategories()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<CategoryResponseDto>>builder()
                        .status("success").statusCode(200).message("Categories fetched successfully").data(list).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<CategoryResponseDto>>>> getAllCategoriesForAdmin() {
        return categoryService.getAllCategoriesForAdmin()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<CategoryResponseDto>>builder()
                        .status("success").statusCode(200).message("Categories fetched successfully").data(list).build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<CategoryResponseDto>>> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<CategoryResponseDto>builder()
                        .status("success").statusCode(200).message("Category fetched successfully").data(r).build()));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CategoryResponseDto>>> createCategory(@Valid @RequestBody CategoryRequestDto dto) {
        return categoryService.createCategory(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<CategoryResponseDto>builder()
                        .status("success").statusCode(201).message("Category created successfully").data(r).build()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CategoryResponseDto>>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryRequestDto dto) {
        return categoryService.updateCategory(id, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<CategoryResponseDto>builder()
                        .status("success").statusCode(200).message("Category updated successfully").data(r).build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteCategory(@PathVariable Long id) {
        return categoryService.deleteCategory(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Category deleted successfully").build()));
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CategoryResponseDto>>> toggleActive(@PathVariable Long id) {
        return categoryService.toggleActive(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<CategoryResponseDto>builder()
                        .status("success").statusCode(200).message("Category status toggled").data(r).build()));
    }
}
