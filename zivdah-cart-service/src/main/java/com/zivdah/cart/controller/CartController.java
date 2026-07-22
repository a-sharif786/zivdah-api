package com.zivdah.cart.controller;

import com.zivdah.cart.dto.ApiResponse;
import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/cart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public Mono<ResponseEntity<ApiResponse<CartItemResponseDto>>> addToCart(
            @Valid @RequestBody CartItemRequestDto dto) {
        return cartService.addToCart(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<CartItemResponseDto>builder()
                        .status("success").statusCode(200)
                        .message("Item added to cart successfully").data(r).build()));
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<CartItemResponseDto>>>> getCartByUser(
            @PathVariable Long userId) {
        return cartService.getCartByUser(userId)
                .collectList()
                .map(items -> ResponseEntity.ok(ApiResponse.<List<CartItemResponseDto>>builder()
                        .status("success").statusCode(200)
                        .message("Cart items retrieved successfully").data(items).build()));
    }

    @PutMapping("/{cartItemId}")
    public Mono<ResponseEntity<ApiResponse<CartItemResponseDto>>> updateQuantity(
            @PathVariable Long cartItemId, @RequestParam Integer quantity) {
        return cartService.updateQuantity(cartItemId, quantity)
                .map(r -> ResponseEntity.ok(ApiResponse.<CartItemResponseDto>builder()
                        .status("success").statusCode(200)
                        .message("Cart item quantity updated successfully").data(r).build()));
    }

    @DeleteMapping("/{cartItemId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> removeItem(@PathVariable Long cartItemId) {
        return cartService.removeItem(cartItemId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200)
                        .message("Cart item removed successfully").build()));
    }

    @DeleteMapping("/clear/{userId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> clearCart(@PathVariable Long userId) {
        return cartService.clearCart(userId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200)
                        .message("Cart cleared successfully").build()));
    }
}
