package com.zivdah.auth.service;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.enums.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<Void> register(RegisterRequestDTO request);
    Mono<LoginResponseDTO> login(LoginRequestDTO request);
    Mono<UserEntity> getUserByMobile(String mobile);
    Mono<UserEntity> getUserById(Long userId);
    Mono<Void> sendOtp(String mobile);
    Mono<String> verifyOtp(VerifyLoginOtpDTO request);
    Flux<AuthUserResponseDTO> getAllUsers();

    // Internal, service-to-service use only (see AuthController's unauthenticated
    // /internal/admin-ids endpoint) — just the ids, to keep an unauthenticated endpoint's
    // data exposure minimal.
    Flux<Long> getAdminUserIds();

    // Registers/refreshes one device's FCM token (upserted by fcmToken — see
    // device_tokens migration). Called at login/verify-otp with deviceType defaulted to WEB,
    // and again any time after login since permission is granted asynchronously and tokens
    // rotate. A user may have several active rows at once (multiple devices/browsers).
    Mono<Void> registerDeviceToken(Long userId, String role, String deviceType, String fcmToken);

    // Internal, service-to-service use only (see AuthController's unauthenticated
    // /internal/device-tokens/{userId} endpoint) — zivdah-notification-service resolves a
    // user's active tokens across every device through this before sending a push.
    Flux<String> getActiveDeviceTokens(Long userId);

    // Internal, service-to-service use only — called by zivdah-notification-service when
    // Firebase reports a token as unregistered/invalid, so future sends skip it.
    Mono<Void> deactivateDeviceToken(String fcmToken);

    Mono<UserResponseDTO> updateProfile(Long userId, UpdateUserProfileDTO dto);
    Mono<Boolean> sendPasswordResetOtp(String email);
    Mono<ResetPasswordResponseDTO> resetPassword(ResetPasswordDTO request);
    Mono<String> verifyRegistrationOtp(VerifyOtpDTO request);

    // fcmToken is optional — when present, only that device's token is deactivated instead
    // of leaving every device's push registration untouched (see LogoutRequestDTO).
    Mono<Void> logout(Long userId, String fcmToken);
    Mono<UserResponseDTO> updateRole(Long userId, Role role);
    Mono<Void> deactivateAccount(Long userId);
    Mono<Void> activateAccount(Long userId);
    Mono<UserStatsResponseDTO> getUserStats();
}
