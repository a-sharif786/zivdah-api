package com.zivdah.cart.controller;

import com.zivdah.cart.dto.ApiResponse;
import com.zivdah.cart.dto.CartItemRequestDto;
import com.zivdah.cart.dto.CartItemResponseDto;
import com.zivdah.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/restful/v1/api/cart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CartController {
    @Autowired
    private  CartService cartService;

    /** Add item to cart */
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartItemResponseDto>> addToCart(
            @Valid @RequestBody CartItemRequestDto dto) {
        CartItemResponseDto response = cartService.addToCart(dto);
        return ResponseEntity.ok(ApiResponse.<CartItemResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Item added to cart successfully")
                .data(response)
                .build());
    }

    /** Get all cart items for a user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<CartItemResponseDto>>> getCartByUser(@PathVariable Long userId) {
        List<CartItemResponseDto> items = cartService.getCartByUser(userId);
        return ResponseEntity.ok(ApiResponse.<List<CartItemResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Cart items retrieved successfully")
                .data(items)
                .build());
    }

    /** Update quantity of a cart item */
    @PutMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponseDto>> updateQuantity(
            @PathVariable Long cartItemId,
            @RequestParam Integer quantity) {
        CartItemResponseDto response = cartService.updateQuantity(cartItemId, quantity);
        return ResponseEntity.ok(ApiResponse.<CartItemResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Cart item quantity updated successfully")
                .data(response)
                .build());
    }

    /** Remove a cart item */
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(@PathVariable Long cartItemId) {
        cartService.removeItem(cartItemId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Cart item removed successfully")
                .data(null)
                .build());
    }

    /** Clear all items in a user's cart */
    @DeleteMapping("/clear/{userId}")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Cart cleared successfully")
                .data(null)
                .build());
    }
}
