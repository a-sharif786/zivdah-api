package com.zivdah.order.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDto {
    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}
