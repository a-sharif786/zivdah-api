package com.zivdah.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDeliveryBoyRequestDto {
    @NotNull(message = "deliveryBoyId is required")
    private Long deliveryBoyId;
}
