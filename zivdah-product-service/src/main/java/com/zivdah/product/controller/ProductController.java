package com.zivdah.product.controller;


import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.dto.WishlistRequestDto;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;


    @PostMapping("/products/create")
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(
            @Valid @RequestBody ProductRequestDto dto) {
        ProductResponseDto response = productService.createProduct(dto);
        return ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Product created successfully")
                .data(response)
                .build());
    }


    @GetMapping("/products/getAll")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getAllProducts( @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<ProductResponseDto> products = productService.getAllProducts(pageable);
        return ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Products retrieved successfully")
                .data(products)
                .build());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProduct(@PathVariable Long id) {
        ProductResponseDto response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Product retrieved successfully")
                .data(response)
                .build());
    }




    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequestDto dto) {
        ProductResponseDto response = productService.updateProduct(id, dto);
        return ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Product updated successfully")
                .data(response)
                .build());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Product deleted successfully")
                .data(null)
                .build());
    }


    @GetMapping("/products/categories")
    public ResponseEntity<ApiResponse<List<ProductCategory>>> getAllCategories() {
        return ResponseEntity.ok(
                ApiResponse.<List<ProductCategory>>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Categories fetched successfully")
                        .data(productService.getAllCategories())
                        .build()
        );
    }

    @GetMapping("/products/wishlist")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getAllWishlist(@RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<ProductResponseDto> wishLists = productService.getAllWishlist(pageable);
        return ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Wishlists retrieved successfully")
                .data(wishLists)
                .build());
    }


    @PutMapping("/products/{id}/wishlist")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateWishlistProduct(
            @PathVariable Long id,
            @Valid @RequestBody WishlistRequestDto dto) {
        ProductResponseDto response = productService.updateWishlist(id, dto.getFav());
        return ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message(dto.getFav() ? "Added to wishlist" : "Removed from wishlist")
                .data(response)
                .build());
    }

}
