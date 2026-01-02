package com.zivdah.auth.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    @Size(min = 10, message = "Password must be at least 10 characters")
    private String mobile;

    @Size(min = 6, message = "min 6 digit")
    private String otp;

}
