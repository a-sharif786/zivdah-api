package com.zivdah.auth.dto;


import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    // Provide either mobile or email to identify the account
    private String mobile;

    @Email(message = "Invalid email")
    private String email;

    // Required only for email login. Mobile login uses OTP via /send-otp + /verify-otp.
    private String password;

    @AssertTrue(message = "Password is required")
    private boolean isPasswordValid() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        // If logging in with email, password must be present. Mobile login doesn't need one.
        return !hasEmail || hasPassword;
    }

}
