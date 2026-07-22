package com.zivdah.cart.service;

import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CartService {
    Mono<CartItemResponseDto> addToCart(CartItemRequestDto request);
    Flux<CartItemResponseDto> getCartByUser(Long userId);
    Mono<CartItemResponseDto> updateQuantity(Long cartItemId, Integer quantity);
    Mono<Void> removeItem(Long cartItemId);
    Mono<Void> clearCart(Long userId);
}
