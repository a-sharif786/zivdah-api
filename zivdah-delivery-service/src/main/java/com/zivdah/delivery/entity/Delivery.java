package com.zivdah.delivery.entity;

import com.zivdah.delivery.enums.DeliveryStatus;
import com.zivdah.delivery.enums.FailureReason;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// One row per (orderId, vendorId) — an order can span multiple vendors (see
// OrderItem.vendorId in zivdah-order-service), and each vendor's portion is packed, picked
// up, and delivered independently.
@Table("deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    private Long id;

    private Long orderId;
    private Long vendorId;
    private Long userId; // customer — denormalized from the order for fast recipient lookup

    private Long deliveryBoyId; // null until an Admin/Vendor assigns one

    private DeliveryStatus status;

    // Set only when status = FAILED
    private FailureReason failureReason;
    private String failureNote;

    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
