package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpDTO {
    @NotBlank(message = "Mobile number is required")
    private String mobile;

    @NotBlank(message = "OTP is required")
    @Size(min = 5, max = 6, message = "OTP must be 5-6 digits")
    private String otp;
    @NotBlank(message = "Device Token is required")
    private String deviceToken;
}