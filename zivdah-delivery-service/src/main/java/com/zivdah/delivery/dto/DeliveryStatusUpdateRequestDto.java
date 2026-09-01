package com.zivdah.delivery.dto;

import com.zivdah.delivery.enums.DeliveryStatus;
import com.zivdah.delivery.enums.FailureReason;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryStatusUpdateRequestDto {
    @NotNull(message = "status is required")
    private DeliveryStatus status;

    // Required when status = FAILED (validated in DeliveryServiceImpl, not here, since it's
    // conditional on another field's value).
    private FailureReason failureReason;
    private String failureNote;
}
