package com.zivdah.cart.controller;

import com.zivdah.cart.dto.ApiResponse;
import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/restful/v1/api/cart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CartController {


    private final CartService cartService;


    /**
     * USER, ADMIN
     */
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Mono<ResponseEntity<ApiResponse<CartItemResponseDto>>> addToCart(
            @Valid @RequestBody CartItemRequestDto dto) {


        log.info("Cart add request received: {}", dto);


        return cartService.addToCart(dto)

                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.<CartItemResponseDto>builder()
                                        .status("success")
                                        .statusCode(201)
                                        .message("Item added to cart successfully")
                                        .data(response)
                                        .build())
                );
    }



    /**
     * USER -> Own Cart
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Mono<ResponseEntity<ApiResponse<List<CartItemResponseDto>>>> getMyCart() {


        return cartService.getMyCart()

                .collectList()

                .map(items ->
                        ResponseEntity.ok(
                                ApiResponse.<List<CartItemResponseDto>>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("Cart items retrieved successfully")
                                        .data(items)
                                        .build()
                        )
                );
    }



    /**
     * ADMIN
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<CartItemResponseDto>>>> getCartByUser(
            @PathVariable Long userId) {


        return cartService.getCartByUser(userId)

                .collectList()

                .map(items ->
                        ResponseEntity.ok(
                                ApiResponse.<List<CartItemResponseDto>>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("Cart retrieved successfully")
                                        .data(items)
                                        .build()
                        )
                );
    }



    /**
     * USER, ADMIN
     */
    @PutMapping("/{cartItemId}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Mono<ResponseEntity<ApiResponse<CartItemResponseDto>>> updateQuantity(
            @PathVariable Long cartItemId,
            @RequestParam Integer quantity) {
        return cartService.updateQuantity(cartItemId, quantity)
                .map(response ->
                        ResponseEntity.ok(
                                ApiResponse.<CartItemResponseDto>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("Cart quantity updated successfully")
                                        .data(response)
                                        .build()
                        )
                );
    }



    /**
     * USER, ADMIN
     */
    @DeleteMapping("/{cartItemId}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Mono<ResponseEntity<ApiResponse<Void>>> removeItem(
            @PathVariable Long cartItemId) {


        return cartService.removeItem(cartItemId)

                .thenReturn(
                        ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("Cart item removed successfully")
                                        .build()
                        )
                );
    }


    /**
     * USER
     * Clear logged-in user's cart
     */
    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('USER')")
    public Mono<ResponseEntity<ApiResponse<Void>>> clearMyCart() {

        return cartService.clearCart()
                .thenReturn(
                        ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("Cart cleared successfully")
                                        .build()
                        )
                );
    }


    /**
     * ADMIN
     * Clear specific user's cart
     */
    @DeleteMapping("/clear/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> clearUserCart(
            @PathVariable Long userId) {

        return cartService.clearCart(userId)
                .thenReturn(
                        ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("User cart cleared successfully")
                                        .build()
                        )
                );
    }

}