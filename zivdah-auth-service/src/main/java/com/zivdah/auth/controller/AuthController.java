package com.zivdah.auth.controller;


import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(
            @Valid @RequestBody RegisterRequestDTO request) {

        authService.register(request);

        ApiResponse<Object> response = ApiResponse.builder()
                .status("success")
                .message("User registered successfully")
                .statusCode(HttpStatus.CREATED.value())
                .data(null) // You can return the registered user or id here
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> loginUser(@RequestBody LoginRequestDTO request) {
        String token = authService.loginWithMobile(request);

        // Fetch user info
        UserEntity userEntity = authService.getUserByMobile(request.getMobile());

        // Build response data (user info + token)
        LoginResponseDTO loginResponse = new LoginResponseDTO(
                userEntity.getId(),
                userEntity.getMobile(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getRole(),
                token
        );


        // Build ApiResponse
        ApiResponse<Object> response = ApiResponse.builder()
                .status("success")
                .message("Login successful")
                .statusCode(HttpStatus.OK.value())
                .data(loginResponse)
                .build();

        return ResponseEntity.ok(response);
    }


    // Step 1: Enter mobile and send OTP
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Object>> sendOtp(@RequestBody MobileRequestDTO request) {
        authService.sendOtp(request.getMobile());

        ApiResponse<Object> response = ApiResponse.builder()
                .status("success")
                .message("OTP sent successfully")
                .statusCode(HttpStatus.OK.value())
                .data(null)
                .build();

        return ResponseEntity.ok(response);
    }

    // Step 2: Verify OTP and login
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Object>> verifyOtp(@RequestBody VerifyOtpDTO request) {
        String token = authService.verifyOtp(request);
        UserEntity userEntity = authService.getUserByMobile(request.getMobile());

        // Build response data (user info + token)
        LoginResponseDTO loginResponse = new LoginResponseDTO(
                userEntity.getId(),
                userEntity.getMobile(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getRole(),
                token
        );
        ApiResponse<Object> response = ApiResponse.builder()
                .status("success")
                .message("Login successful")
                .statusCode(HttpStatus.OK.value())
                .data(loginResponse)
                .build();

        return ResponseEntity.ok(response);
    }


    @GetMapping("/byMobile/{mobile}")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> getUserByMobile(@PathVariable String mobile) {

        UserEntity userEntity = authService.getUserByMobile(mobile);

        LoginResponseDTO loginResponse = new LoginResponseDTO(
                userEntity.getId(),
                userEntity.getMobile(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getRole(),
                null // token not needed here
        );

        return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("User fetched successfully")
                .data(loginResponse)
                .build());
    }


    @GetMapping("/all-users")
    public ResponseEntity<ApiResponse<List<AuthUserResponseDTO>>> getAllUsers() {

        List<AuthUserResponseDTO> users = authService.getAllUsers();

        return ResponseEntity.ok(
                ApiResponse.<List<AuthUserResponseDTO>>builder()
                        .status("success")
                        .statusCode(HttpStatus.OK.value())
                        .message("Users fetched successfully")
                        .data(users)
                        .build()
        );
    }


    @PutMapping("/update-profile/{mobile}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateProfile(
            @PathVariable String mobile,
            @Valid @RequestBody UpdateUserProfileDTO dto) {

        UserResponseDTO updated = authService.updateProfile(mobile, dto);

        return ResponseEntity.ok(
                ApiResponse.<UserResponseDTO>builder()
                        .status("success")
                        .statusCode(200)
                        .message("Profile updated successfully")
                        .data(updated)
                        .build()
        );
    }

    @PostMapping("/forget-password")
    public ResponseEntity<ApiResponse<EmailResponseDTO>> forgetPassword(
            @Valid @RequestBody EmailRequestDTO request) {

        // Call service to send OTP
        boolean sent = authService.sendPasswordResetOtp(request.getEmail());

        // Build response DTO
        EmailResponseDTO emailResponse = EmailResponseDTO.builder()
                .status(sent ? "success" : "failure")
                .message(sent ? "Password reset OTP sent to your email" : "Email not found")
                .build();

        // Wrap in ApiResponse
        ApiResponse<EmailResponseDTO> apiResponse = ApiResponse.<EmailResponseDTO>builder()
                .status(emailResponse.getStatus())
                .statusCode(HttpStatus.OK.value())
                .message(emailResponse.getMessage())
                .data(emailResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }






    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<ResetPasswordResponseDTO>> resetPassword(
            @Valid @RequestBody ResetPasswordDTO request) {

        ResetPasswordResponseDTO response = authService.resetPassword(request);

        return ResponseEntity.ok(
                ApiResponse.<ResetPasswordResponseDTO>builder()
                        .status(response.getStatus())
                        .statusCode(HttpStatus.OK.value())
                        .message(response.getMessage())
                        .data(response)
                        .build()
        );
    }






}
