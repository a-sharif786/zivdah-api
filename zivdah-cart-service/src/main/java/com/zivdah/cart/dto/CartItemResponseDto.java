package com.zivdah.cart.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CartItemResponseDto {

    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;

    private String sku;

    private String status;  // ACTIVE, SAVED, REMOVED

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
