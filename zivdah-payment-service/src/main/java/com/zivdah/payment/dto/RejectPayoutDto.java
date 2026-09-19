package com.zivdah.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectPayoutDto {
    @NotBlank(message = "A reason is required")
    private String reason;
}
