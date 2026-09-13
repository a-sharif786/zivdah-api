package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Narrow projection of delivery-service's DeliveryResponseDto — what the DELIVERY_TIME intent
// needs (id/orderId/userId/status/updatedAt), plus vendorId/deliveryBoyId for the order-context
// panel's name-resolution (GET /conversations/{id}/order-context, via AuthServiceClient).
@Getter
@Setter
public class DeliverySummaryDto {
    private Long id;
    private Long orderId;
    private Long userId;
    private Long vendorId;
    private Long deliveryBoyId;
    private String status;
    private LocalDateTime updatedAt;
}
