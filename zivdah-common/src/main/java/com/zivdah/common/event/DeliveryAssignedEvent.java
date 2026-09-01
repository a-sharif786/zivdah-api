package com.zivdah.common.event;

import lombok.*;

/**
 * Published by zivdah-delivery-service when an Admin/Vendor assigns a delivery boy to a
 * vendor-portion of an order (POST /delivery/{id}/assign). Consumed by
 * zivdah-notification-service to notify the delivery boy, the customer, the owning vendor,
 * and all admins.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAssignedEvent {
    private Long deliveryId;
    private Long orderId;
    private Long vendorId;
    private Long userId;
    private Long deliveryBoyId;
    private Long assignedByUserId;
    private String assignedByRole;
}
