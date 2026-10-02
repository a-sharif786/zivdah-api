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
    Mono<LoginResponseDTO> verifyOtp(VerifyLoginOtpDTO request);

   
    Mono<LoginResponseDTO> refreshToken(RefreshTokenRequestDTO request);
    Flux<AuthUserResponseDTO> getAllUsers();

    Flux<AuthUserResponseDTO> getUsersByRole(Role role);

    
    Flux<Long> getAdminUserIds();

    Mono<Void> registerDeviceToken(Long userId, String role, String deviceType, String fcmToken);

    Flux<String> getActiveDeviceTokens(Long userId);

  
    Mono<Void> deactivateDeviceToken(String fcmToken);

    Mono<UserResponseDTO> updateProfile(Long userId, UpdateUserProfileDTO dto);
    Mono<BankDetailsResponseDTO> getBankDetails(Long userId);
    Mono<Boolean> sendPasswordResetOtp(String email);
    Mono<ResetPasswordResponseDTO> resetPassword(ResetPasswordDTO request);
    Mono<LoginResponseDTO> verifyRegistrationOtp(VerifyOtpDTO request);
    Mono<Void> logout(Long userId, String fcmToken, String refreshToken);
    Mono<UserResponseDTO> updateRole(Long userId, Role role);
    Mono<Void> deactivateAccount(Long userId);
    Mono<Void> activateAccount(Long userId);
    Mono<UserStatsResponseDTO> getUserStats();
}
