package com.zivdah.order.dto;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDto {
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;
}
