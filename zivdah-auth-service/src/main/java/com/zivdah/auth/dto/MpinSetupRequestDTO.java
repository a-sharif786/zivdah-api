package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MpinSetupRequestDTO {

    // Stable per-install id the app generates once (e.g. a UUID) and keeps in secure storage.
    @NotBlank(message = "deviceId is required")
    @Pattern(regexp = "^[A-Za-z0-9._:-]{8,64}$", message = "deviceId must be 8-64 characters: letters, digits, . _ : -")
    private String deviceId;

    // Shown in the device list, e.g. "Pixel 8". Optional.
    @Size(max = 100, message = "deviceName must be at most 100 characters")
    private String deviceName;

    @NotBlank(message = "MPIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "MPIN must be exactly 6 digits")
    private String mpin;
}
