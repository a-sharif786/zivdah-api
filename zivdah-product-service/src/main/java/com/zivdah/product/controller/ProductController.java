package com.zivdah.product.controller;

import com.zivdah.product.dto.ApiResponse;
import com.zivdah.product.dto.ProductRequestDto;
import com.zivdah.product.dto.ProductResponseDto;
import com.zivdah.product.dto.WishlistRequestDto;
import com.zivdah.product.enums.ProductCategory;
import com.zivdah.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/restful/v1/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    private Mono<String> currentRole() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> auth.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.replaceFirst("^ROLE_", ""))
                        .orElse(""));
    }

    @PostMapping(value = "/products/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> createProduct(
            @Valid @RequestPart("data") ProductRequestDto dto,
            @RequestPart("image") FilePart image) {
        log.info("Controller reached");
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> productService.createProduct(dto, image, t.getT1(), t.getT2()))
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

    @GetMapping("/products/search")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return productService.searchProducts(keyword, PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                        .status("success").statusCode(200).message("Products retrieved successfully").data(list).build()));
    }

    @GetMapping("/products/category/{category}")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> getProductsByCategory(
            @PathVariable ProductCategory category,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return productService.getProductsByCategory(category, PageRequest.of(page, size))
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

    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> updateProduct(
            @PathVariable Long id, @Valid @RequestPart("data") ProductRequestDto dto,
            @RequestPart(value = "image", required = false) FilePart image) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> productService.updateProduct(id, dto, image, t.getT1(), t.getT2()))
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200).message("Product updated successfully").data(r).build()));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteProduct(@PathVariable Long id) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> productService.deleteProduct(id, t.getT1(), t.getT2()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Product deleted successfully").build()));
    }

    @GetMapping("/products/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> getProductsByVendor(
            @PathVariable Long vendorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> {
                    if ("VENDOR".equalsIgnoreCase(t.getT2()) && !t.getT1().equals(vendorId)) {
                        return Mono.<List<ProductResponseDto>>error(
                                new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendors may only query their own products"));
                    }
                    return productService.getProductsByVendor(vendorId, PageRequest.of(page, size)).collectList();
                })
                .map(list -> ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                        .status("success").statusCode(200).message("Vendor products retrieved").data(list).build()));
    }

    @GetMapping("/products/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Long>>> countProducts() {
        return productService.countProducts()
                .map(count -> ResponseEntity.ok(ApiResponse.<Long>builder()
                        .status("success").statusCode(200).message("Product count retrieved").data(count).build()));
    }

    @GetMapping("/products/categories")
    public Mono<ResponseEntity<ApiResponse<List<ProductCategory>>>> getAllCategories() {
        return productService.getAllCategories()
                .map(cats -> ResponseEntity.ok(ApiResponse.<List<ProductCategory>>builder()
                        .status("success").statusCode(200).message("Categories fetched").data(cats).build()));
    }

    @GetMapping("/products/wishlist")
    public Mono<ResponseEntity<ApiResponse<List<ProductResponseDto>>>> getWishlist(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return currentUserId()
                .flatMapMany(userId -> productService.getWishlist(userId, PageRequest.of(page, size)))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<ProductResponseDto>>builder()
                        .status("success").statusCode(200).message("Wishlist retrieved successfully").data(list).build()));
    }

    @PutMapping("/products/{id}/wishlist")
    public Mono<ResponseEntity<ApiResponse<ProductResponseDto>>> updateWishlist(
            @PathVariable Long id, @Valid @RequestBody WishlistRequestDto dto) {
        return currentUserId()
                .flatMap(userId -> productService.updateWishlist(userId, id, dto.getFav()))
                .map(r -> ResponseEntity.ok(ApiResponse.<ProductResponseDto>builder()
                        .status("success").statusCode(200)
                        .message(Boolean.TRUE.equals(dto.getFav()) ? "Added to wishlist" : "Removed from wishlist")
                        .data(r).build()));
    }
}
