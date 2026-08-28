package com.zivdah.auth.controller;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    private Mono<Authentication> currentAuth() {
        return ReactiveSecurityContextHolder.getContext().map(ctx -> ctx.getAuthentication());
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Mono<Void> requireOwnerOrAdmin(Long userId) {
        return currentAuth().flatMap(auth -> {
            if (auth.getName().equals(String.valueOf(userId)) || isAdmin(auth)) {
                return Mono.empty();
            }
            return Mono.error(new AccessDeniedException("Not authorized to access this profile"));
        });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<ApiResponse<Object>>> register(@Valid @RequestBody RegisterRequestDTO request) {
        return authService.register(request)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .<ApiResponse<Object>>body(ApiResponse.builder()
                                .status("success").message("User registered successfully")
                                .statusCode(201).data(null).build()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<Object>>> loginUser(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request)
                .map(loginResp -> ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").message("Login successful")
                        .statusCode(200).data(loginResp).build()));
    }

    @PostMapping("/send-otp")
    public Mono<ResponseEntity<ApiResponse<Object>>> sendOtp(@RequestBody MobileRequestDTO request) {
        return authService.sendOtp(request.getMobile())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").message("OTP sent successfully").statusCode(200).data(null).build()));
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<ApiResponse<Object>>> verifyOtp(@Valid @RequestBody VerifyLoginOtpDTO request) {
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

    @GetMapping("/byUserId/{userId}")
    public Mono<ResponseEntity<ApiResponse<LoginResponseDTO>>> getUserById(@PathVariable Long userId) {
        return requireOwnerOrAdmin(userId)
                .then(authService.getUserById(userId))
                .map(user -> {
                    LoginResponseDTO resp = new LoginResponseDTO(user.getId(), user.getMobile(),
                            user.getName(), user.getEmail(), user.getRole(), null);
                    return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                            .status("success").statusCode(200).message("User fetched successfully").data(resp).build());
                });
    }

    @GetMapping("/all-users")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<AuthUserResponseDTO>>>> getAllUsers() {
        return authService.getAllUsers().collectList()
                .map(users -> ResponseEntity.ok(ApiResponse.<List<AuthUserResponseDTO>>builder()
                        .status("success").statusCode(200).message("Users fetched successfully").data(users).build()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserStatsResponseDTO>>> getUserStats() {
        return authService.getUserStats()
                .map(stats -> ResponseEntity.ok(ApiResponse.<UserStatsResponseDTO>builder()
                        .status("success").statusCode(200).message("User stats retrieved").data(stats).build()));
    }

    @PutMapping("/update-profile/{userId}")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> updateProfile(
            @PathVariable Long userId, @Valid @RequestBody UpdateUserProfileDTO dto) {
        return requireOwnerOrAdmin(userId)
                .then(authService.updateProfile(userId, dto))
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

    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Object>>> logout() {
        return currentAuth()
                .flatMap(auth -> authService.logout(Long.valueOf(auth.getName())))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").message("Logged out successfully").statusCode(200).data(null).build()));
    }

    @PutMapping("/update-role/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> updateRole(
            @PathVariable Long userId, @Valid @RequestBody UpdateRoleDTO dto) {
        return authService.updateRole(userId, dto.getRole())
                .map(r -> ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                        .status("success").statusCode(200).message("Role updated successfully").data(r).build()));
    }

    @PutMapping("/deactivate/{userId}")
    public Mono<ResponseEntity<ApiResponse<Object>>> deactivateAccount(@PathVariable Long userId) {
        return requireOwnerOrAdmin(userId)
                .then(authService.deactivateAccount(userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").statusCode(200).message("Account deactivated successfully").data(null).build()));
    }

    @PutMapping("/activate/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Object>>> activateAccount(@PathVariable Long userId) {
        return authService.activateAccount(userId)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").statusCode(200).message("Account activated successfully").data(null).build()));
    }
}
