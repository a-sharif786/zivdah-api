package com.zivdah.auth.service;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;

import java.util.List;

public interface AuthService {

    void register(RegisterRequestDTO request);

    String loginWithMobile(LoginRequestDTO request);

    UserEntity getUserByMobile(String mobile);

    void sendOtp(String mobile);

    String verifyOtp(VerifyOtpDTO request);

    List<AuthUserResponseDTO> getAllUsers();

    UserResponseDTO updateProfile(String mobile, UpdateUserProfileDTO dto);
    boolean sendPasswordResetOtp(String email);

    ResetPasswordResponseDTO resetPassword(ResetPasswordDTO request);
    String verifyRegistrationOtp(VerifyOtpDTO request);

}