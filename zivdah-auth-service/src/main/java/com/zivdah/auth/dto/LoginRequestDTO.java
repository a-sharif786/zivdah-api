package com.zivdah.auth.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    // Provide either mobile or email to identify the account
    private String mobile;

    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

}
