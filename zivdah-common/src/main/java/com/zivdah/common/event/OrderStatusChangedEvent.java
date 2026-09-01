package com.zivdah.common.event;

import lombok.*;

/**
 * Published by zivdah-order-service whenever an order's status changes via
 * PATCH /orders/{id}/status or PUT /orders/cancel/{id} — NOT for the create-time event
 * (see OrderCreatedEvent) or the payment-driven CREATED->PAID/CANCELLED transition (see
 * PaymentCompletedEvent, which already covers payment success/failure notifications).
 *
 * {@code changedByRole} is what tells "vendor rejected" (changedByRole=VENDOR, newStatus=
 * CANCELLED) apart from a generic cancellation (anyone else) in zivdah-notification-service's
 * consumer — no separate OrderStatus value needed for that distinction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusChangedEvent {
    private Long orderId;
    private Long userId;
    private String oldStatus;
    private String newStatus;
    private Long changedByUserId;
    private String changedByRole;

    // Null for order-service's own publishes (order-level transitions have no single vendor).
    // Set by zivdah-delivery-service's publishes (PACKED/READY_FOR_PICKUP/PICKED_UP/
    // ON_THE_WAY) so zivdah-notification-service can notify the right vendor/delivery boy
    // without an extra OrderServiceClient.getVendorIds lookup.
    private Long vendorId;
    private Long deliveryBoyId;
}
