package com.zivdah.cart.serviceImpl;

import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.entity.CartItemEntity;
import com.zivdah.cart.enums.CartItemStatus;
import com.zivdah.cart.exception.ResourceNotFoundException;
import com.zivdah.cart.repository.CartRepository;
import com.zivdah.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;

    @Override
    public Mono<CartItemResponseDto> addToCart(CartItemRequestDto request) {
        CartItemEntity item = CartItemEntity.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .subtotal(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                .sku(request.getSku())
                .status(CartItemStatus.ACTIVE)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return cartRepository.save(item).map(this::mapToDto);
    }

    @Override
    public Flux<CartItemResponseDto> getCartByUser(Long userId) {
        return cartRepository.findByUserId(userId).map(this::mapToDto);
    }

    @Override
    public Mono<CartItemResponseDto> updateQuantity(Long cartItemId, Integer quantity) {
        return cartRepository.findById(cartItemId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found: " + cartItemId)))
                .flatMap(item -> {
                    item.setQuantity(quantity);
                    item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
                    item.setUpdatedAt(LocalDateTime.now());
                    return cartRepository.save(item);
                })
                .map(this::mapToDto);
    }

    @Override
    public Mono<Void> removeItem(Long cartItemId) {
        return cartRepository.existsById(cartItemId)
                .flatMap(exists -> {
                    if (!exists) return Mono.<Void>error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found: " + cartItemId));
                    return cartRepository.deleteById(cartItemId);
                });
    }

    @Override
    public Mono<Void> clearCart(Long userId) {
        return cartRepository.deleteByUserId(userId);
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
                .status(item.getStatus() != null ? item.getStatus().name() : null)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
