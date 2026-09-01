package com.zivdah.delivery.dto;

import com.zivdah.delivery.enums.DeliveryStatus;
import com.zivdah.delivery.enums.FailureReason;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResponseDto {
    private Long id;
    private Long orderId;
    private Long vendorId;
    private Long userId;
    private Long deliveryBoyId;
    private DeliveryStatus status;
    private FailureReason failureReason;
    private String failureNote;
    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
