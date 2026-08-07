package com.zivdah.auth.dto;


import com.zivdah.auth.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {
    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email")
    @NotBlank(message = "Email is required")
    private String email;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Size(min = 10, message = "Mobile number must be at least 10 digits")
    private String mobile;

    // Optional: USER (default) or VENDOR. ADMIN cannot be self-registered.
    private Role role;

}
