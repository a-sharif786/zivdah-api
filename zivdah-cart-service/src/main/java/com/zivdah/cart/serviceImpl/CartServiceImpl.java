package com.zivdah.cart.serviceImpl;

import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.entity.CartItemEntity;
import com.zivdah.cart.enums.CartItemStatus;
import com.zivdah.cart.exception.ResourceNotFoundException;
import com.zivdah.cart.repository.CartRepository;
import com.zivdah.cart.service.CartService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    @Autowired
    private CartRepository cartRepository;
    @Override
    public CartItemResponseDto addToCart(CartItemRequestDto request) {
        CartItemEntity cartItem = CartItemEntity.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .subtotal(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                .sku(request.getSku())
                .status(CartItemStatus.ACTIVE)
                .deleted(false)
                .build();

        CartItemEntity savedItem = cartRepository.save(cartItem);
        return mapToDto(savedItem);
    }

    @Override
    public List<CartItemResponseDto> getCartByUser(Long userId) {
        List<CartItemEntity> items = cartRepository.findByUserId(userId);
        return items.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public CartItemResponseDto updateQuantity(Long cartItemId, Integer quantity) {
        CartItemEntity item = cartRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with ID: " + cartItemId));
        item.setQuantity(quantity);
        item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
        CartItemEntity updatedItem = cartRepository.save(item);
        return mapToDto(updatedItem);
    }

    @Override
    public void removeItem(Long cartItemId) {
        if (!cartRepository.existsById(cartItemId)) {
            throw new ResourceNotFoundException("Cart item not found with ID: " + cartItemId);
        }
        cartRepository.deleteById(cartItemId);
    }

    @Override
    public void clearCart(Long userId) {
        cartRepository.deleteByUserId(userId);
    }


    private CartItemResponseDto mapToDto(CartItemEntity item) {
        return CartItemResponseDto.builder()
                .id(item.getId())
                .userId(item.getUserId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .sku(item.getSku())
                .status(item.getStatus().name())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
