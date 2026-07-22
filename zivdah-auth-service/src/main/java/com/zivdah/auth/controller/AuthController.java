package com.zivdah.auth.controller;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Mono<ResponseEntity<ApiResponse<Object>>> register(@Valid @RequestBody RegisterRequestDTO request) {
        return authService.register(request)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .<ApiResponse<Object>>body(ApiResponse.builder()
                                .status("success").message("User registered successfully")
                                .statusCode(201).data(null).build()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<Object>>> loginUser(@RequestBody LoginRequestDTO request) {
        return authService.loginWithMobile(request)
                .flatMap(token -> authService.getUserByMobile(request.getMobile())
                        .map(user -> {
                            LoginResponseDTO loginResp = new LoginResponseDTO(
                                    user.getId(), user.getMobile(), user.getName(),
                                    user.getEmail(), user.getRole(), token);
                            return ResponseEntity.ok(ApiResponse.<Object>builder()
                                    .status("success").message("Login successful")
                                    .statusCode(200).data(loginResp).build());
                        }));
    }

    @PostMapping("/send-otp")
    public Mono<ResponseEntity<ApiResponse<Object>>> sendOtp(@RequestBody MobileRequestDTO request) {
        return authService.sendOtp(request.getMobile())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").message("OTP sent successfully").statusCode(200).data(null).build()));
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<ApiResponse<Object>>> verifyOtp(@RequestBody VerifyOtpDTO request) {
        return authService.verifyOtp(request)
                .flatMap(token -> authService.getUserByMobile(request.getMobile())
                        .map(user -> {
                            LoginResponseDTO loginResp = new LoginResponseDTO(
                                    user.getId(), user.getMobile(), user.getName(),
                                    user.getEmail(), user.getRole(), token);
                            return ResponseEntity.ok(ApiResponse.<Object>builder()
                                    .status("success").message("Login successful")
                                    .statusCode(200).data(loginResp).build());
                        }));
    }

    @GetMapping("/byMobile/{mobile}")
    public Mono<ResponseEntity<ApiResponse<LoginResponseDTO>>> getUserByMobile(@PathVariable String mobile) {
        return authService.getUserByMobile(mobile)
                .map(user -> {
                    LoginResponseDTO resp = new LoginResponseDTO(user.getId(), user.getMobile(),
                            user.getName(), user.getEmail(), user.getRole(), null);
                    return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                            .status("success").statusCode(200).message("User fetched successfully").data(resp).build());
                });
    }

    @GetMapping("/all-users")
    public Mono<ResponseEntity<ApiResponse<List<AuthUserResponseDTO>>>> getAllUsers() {
        return authService.getAllUsers().collectList()
                .map(users -> ResponseEntity.ok(ApiResponse.<List<AuthUserResponseDTO>>builder()
                        .status("success").statusCode(200).message("Users fetched successfully").data(users).build()));
    }

    @PutMapping("/update-profile/{mobile}")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> updateProfile(
            @PathVariable String mobile, @Valid @RequestBody UpdateUserProfileDTO dto) {
        return authService.updateProfile(mobile, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                        .status("success").statusCode(200).message("Profile updated successfully").data(r).build()));
    }

    @PostMapping("/forget-password")
    public Mono<ResponseEntity<ApiResponse<EmailResponseDTO>>> forgetPassword(
            @Valid @RequestBody EmailRequestDTO request) {
        return authService.sendPasswordResetOtp(request.getEmail())
                .map(sent -> {
                    EmailResponseDTO emailResp = EmailResponseDTO.builder()
                            .status(sent ? "success" : "failure")
                            .message(sent ? "OTP sent to your email" : "Email not found")
                            .build();
                    return ResponseEntity.ok(ApiResponse.<EmailResponseDTO>builder()
                            .status(emailResp.getStatus()).statusCode(200)
                            .message(emailResp.getMessage()).data(emailResp).build());
                });
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<ApiResponse<ResetPasswordResponseDTO>>> resetPassword(
            @Valid @RequestBody ResetPasswordDTO request) {
        return authService.resetPassword(request)
                .map(r -> ResponseEntity.ok(ApiResponse.<ResetPasswordResponseDTO>builder()
                        .status(r.getStatus()).statusCode(200).message(r.getMessage()).data(r).build()));
    }

    @PostMapping("/verify-registration-otp")
    public Mono<ResponseEntity<ApiResponse<LoginResponseDTO>>> verifyRegistrationOtp(
            @Valid @RequestBody VerifyOtpDTO request) {
        return authService.verifyRegistrationOtp(request)
                .flatMap(token -> authService.getUserByMobile(request.getMobile())
                        .map(user -> {
                            LoginResponseDTO loginResp = new LoginResponseDTO(
                                    user.getId(), user.getMobile(), user.getName(),
                                    user.getEmail(), user.getRole(), token);
                            return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                                    .status("success").message("User verified and logged in")
                                    .statusCode(200).data(loginResp).build());
                        }));
    }
}
