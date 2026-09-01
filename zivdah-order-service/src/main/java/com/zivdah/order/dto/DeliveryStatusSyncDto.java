package com.zivdah.order.dto;

import lombok.*;

// Body for the internal PUT /orders/{orderId}/delivery-status — deliveryStatus is one of
// zivdah-delivery-service's own status values (e.g. "ON_THE_WAY", "DELIVERED"), not this
// service's OrderStatus enum. See OrderServiceImpl#syncDeliveryStatus for the mapping.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusSyncDto {
    private String deliveryStatus;
}
