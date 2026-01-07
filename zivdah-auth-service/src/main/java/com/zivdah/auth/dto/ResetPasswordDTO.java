package com.zivdah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDTO {
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 5, max = 6)
    private String otp;

    @NotBlank
    @Size(min = 6)
    private String newPassword;
}