package com.zivdah.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemRequestDto {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    /** Optional SKU / variant */
    private String sku;
}
