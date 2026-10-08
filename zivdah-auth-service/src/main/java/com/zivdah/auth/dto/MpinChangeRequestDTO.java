package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MpinChangeRequestDTO {

    @NotBlank(message = "deviceId is required")
    @Size(max = 64, message = "deviceId must be at most 64 characters")
    private String deviceId;

    @NotBlank(message = "Current MPIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "Current MPIN must be exactly 6 digits")
    private String oldMpin;

    @NotBlank(message = "New MPIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "New MPIN must be exactly 6 digits")
    private String newMpin;
}
