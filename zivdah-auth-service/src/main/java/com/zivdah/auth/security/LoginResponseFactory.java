package com.zivdah.auth.security;

import com.zivdah.auth.dto.LoginResponseDTO;
import com.zivdah.auth.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

// Shared by every flow that signs a user in (password, OTP, registration OTP, refresh, MPIN), so
// they all return the identical LoginResponseDTO shape.
@Component
@RequiredArgsConstructor
public class LoginResponseFactory {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    // New login = new access token + a new refresh-token family. authTime: LocalDateTime.now() for
    // a full (password/OTP) login, null for an MPIN login — see RefreshToken#authTime.
    public Mono<LoginResponseDTO> issue(UserEntity user, LocalDateTime authTime) {
        return refreshTokenService.issue(user.getId(), null, authTime)
                .map(refreshToken -> toLoginResponse(user, refreshToken, authTime));
    }

    public LoginResponseDTO toLoginResponse(UserEntity user, String refreshToken, LocalDateTime authTime) {
        String token = jwtTokenProvider.generateToken(user.getId(), user.getMobile(), user.getRole().name(),
                authTime == null ? null : authTime.atZone(ZoneId.systemDefault()).toInstant());
        return LoginResponseDTO.builder()
                .id(user.getId())
                .mobile(user.getMobile())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .refreshExpiresIn(refreshTokenService.getRefreshTokenExpirySeconds())
                .build();
    }
}
