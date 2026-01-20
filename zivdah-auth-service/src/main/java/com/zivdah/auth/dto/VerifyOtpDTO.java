package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpDTO {
    @NotBlank(message = "Mobile number is required")
    private String mobile;

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Mobile OTP is required")
    @Size(min = 5, max = 6, message = "Mobile OTP must be 5-6 digits")
    private String mobileOtp;

    @NotBlank(message = "Email OTP is required")
    @Size(min = 5, max = 6, message = "Email OTP must be 5-6 digits")
    private String emailOtp;

    @NotBlank(message = "Device Token is required")
    private String deviceToken;
}