package com.zivdah.common.event;

import lombok.*;

/**
 * Published by zivdah-delivery-service when a delivery boy marks a delivery FAILED
 * (PATCH /delivery/{id}/status). Consumed by zivdah-notification-service to notify the
 * customer, the owning vendor, and all admins — deliberately NOT the delivery boy who
 * performed the action (same "don't notify the actor" rule as a vendor-rejected order).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryFailedEvent {
    private Long deliveryId;
    private Long orderId;
    private Long vendorId;
    private Long userId;
    private Long deliveryBoyId;

    // CUSTOMER_NOT_AVAILABLE, WRONG_ADDRESS, CUSTOMER_REFUSED, PAYMENT_ISSUE,
    // DAMAGED_ORDER, OTHER — see com.zivdah.delivery.enums.FailureReason
    private String failureReason;
    // Free-text detail, expected (but not required) when failureReason = OTHER
    private String failureNote;
}
