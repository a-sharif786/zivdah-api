package com.zivdah.common.event;

import lombok.*;
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
    private Long vendorId;
    private Long deliveryBoyId;
}
