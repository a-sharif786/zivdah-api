package com.zivdah.cart.service;

import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.entity.CartItemEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface CartService {
    CartItemResponseDto addToCart(CartItemRequestDto request);

    List<CartItemResponseDto> getCartByUser(Long userId);

    CartItemResponseDto updateQuantity(Long cartItemId, Integer quantity);

    void removeItem(Long cartItemId);

    void clearCart(Long userId);
}
