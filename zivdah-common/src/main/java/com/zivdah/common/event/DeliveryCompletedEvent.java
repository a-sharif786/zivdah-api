package com.zivdah.common.event;

import lombok.*;

/**
 * Published by zivdah-delivery-service when a delivery boy marks a delivery DELIVERED
 * (PATCH /delivery/{id}/status). Consumed by zivdah-notification-service to notify the
 * customer, the owning vendor, and all admins.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompletedEvent {
    private Long deliveryId;
    private Long orderId;
    private Long vendorId;
    private Long userId;
    private Long deliveryBoyId;
}
