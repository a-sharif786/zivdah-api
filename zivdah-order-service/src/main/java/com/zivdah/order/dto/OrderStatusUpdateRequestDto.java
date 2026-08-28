package com.zivdah.order.dto;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequestDto {
    private OrderStatus status;
}
