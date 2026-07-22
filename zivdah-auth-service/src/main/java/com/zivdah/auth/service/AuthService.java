package com.zivdah.auth.service;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<Void> register(RegisterRequestDTO request);
    Mono<String> loginWithMobile(LoginRequestDTO request);
    Mono<UserEntity> getUserByMobile(String mobile);
    Mono<Void> sendOtp(String mobile);
    Mono<String> verifyOtp(VerifyOtpDTO request);
    Flux<AuthUserResponseDTO> getAllUsers();
    Mono<UserResponseDTO> updateProfile(String mobile, UpdateUserProfileDTO dto);
    Mono<Boolean> sendPasswordResetOtp(String email);
    Mono<ResetPasswordResponseDTO> resetPassword(ResetPasswordDTO request);
    Mono<String> verifyRegistrationOtp(VerifyOtpDTO request);
}
