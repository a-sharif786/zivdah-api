package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MpinLoginRequestDTO {

    @NotBlank(message = "Mobile is required")
    private String mobile;

    @NotBlank(message = "deviceId is required")
    @Size(max = 64, message = "deviceId must be at most 64 characters")
    private String deviceId;

    @NotBlank(message = "deviceSecret is required")
    @Size(max = 128, message = "deviceSecret must be at most 128 characters")
    private String deviceSecret;

    @NotBlank(message = "MPIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "MPIN must be exactly 6 digits")
    private String mpin;
}
