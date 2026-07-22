package com.zivdah.product.controller;

import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.dto.WishlistRequestDto;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @PostMapping("/products/create")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> createProduct(
            @Valid @RequestBody ProductRequestDto dto) {
        return productService.createProduct(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200).message("Product created successfully").data(r).build()));
    }

    @GetMapping("/products/getAll")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return productService.getAllProducts(PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                        .status("success").statusCode(200).message("Products retrieved successfully").data(list).build()));
    }

    @GetMapping("/products/{id}")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200).message("Product retrieved successfully").data(r).build()));
    }

    @PutMapping("/products/{id}")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequestDto dto) {
        return productService.updateProduct(id, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200).message("Product updated successfully").data(r).build()));
    }

    @DeleteMapping("/products/{id}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Product deleted successfully").build()));
    }

    @GetMapping("/products/categories")
    public Mono<ResponseEntity<ApiResponse<List<ProductCategory>>>> getAllCategories() {
        return productService.getAllCategories()
                .map(cats -> ResponseEntity.ok(ApiResponse.<List<ProductCategory>>builder()
                        .status("success").statusCode(200).message("Categories fetched").data(cats).build()));
    }

    @GetMapping("/products/wishlist")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> getAllWishlist(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return productService.getAllWishlist(PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                        .status("success").statusCode(200).message("Wishlists retrieved successfully").data(list).build()));
    }

    @PutMapping("/products/{id}/wishlist")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> updateWishlist(
            @PathVariable Long id, @Valid @RequestBody WishlistRequestDto dto) {
        return productService.updateWishlist(id, dto.getFav())
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200)
                        .message(Boolean.TRUE.equals(dto.getFav()) ? "Added to wishlist" : "Removed from wishlist")
                        .data(r).build()));
    }
}
