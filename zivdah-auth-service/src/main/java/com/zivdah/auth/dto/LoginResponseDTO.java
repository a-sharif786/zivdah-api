package com.zivdah.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zivdah.auth.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Returned identically by /login, /verify-otp, /verify-registration-otp and /refresh-token.
// `token` is kept (same value as accessToken) for clients that predate refresh tokens.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponseDTO {

    private Long id;
    private String mobile;
    private String name;

    private String email;
    private Role role;
    private String token;

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    // Seconds until accessToken / refreshToken expire.
    private Long expiresIn;
    private Long refreshExpiresIn;

    public LoginResponseDTO(Long id, String mobile, String name, String email, Role role, String token) {
        this.id = id;
        this.mobile = mobile;
        this.name = name;
        this.email = email;
        this.role = role;
        this.token = token;
    }
}
