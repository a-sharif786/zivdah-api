package com.zivdah.order.dto;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatsResponseDto {
    // All-time count, unaffected by the from/to range below.
    private long totalOrders;

    // Everything below is scoped to the requested [from, to] range.
    private long ordersInRange;
    private Map<OrderStatus, Long> statusBreakdown;
    private BigDecimal revenueInRange;
}
