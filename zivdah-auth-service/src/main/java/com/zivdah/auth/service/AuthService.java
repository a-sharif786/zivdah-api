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
    Mono<UserResponseDTO> updateProfile(Long userId, UpdateUserProfileDTO dto);
    Mono<Boolean> sendPasswordResetOtp(String email);
    Mono<ResetPasswordResponseDTO> resetPassword(ResetPasswordDTO request);
    Mono<String> verifyRegistrationOtp(VerifyOtpDTO request);
    Mono<Void> logout(Long userId);
    Mono<UserResponseDTO> updateRole(Long userId, Role role);
    Mono<Void> deactivateAccount(Long userId);
    Mono<Void> activateAccount(Long userId);
}
