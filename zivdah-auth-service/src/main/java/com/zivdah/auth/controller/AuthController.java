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
import org.springframework.security.core.GrantedAuthority;
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

    private Mono<String> currentRole() {
        return currentAuth().map(auth -> auth.getAuthorities().stream().findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .orElse(""));
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

    // Internal, no auth — see SecurityConfig. Called by zivdah-notification-service to fan
    // out admin notifications; no user JWT available for that call. Returns only ids (not
    // the full user list /all-users exposes) to keep an unauthenticated endpoint's exposure
    // minimal.
    @GetMapping("/internal/admin-ids")
    public Mono<ResponseEntity<ApiResponse<List<Long>>>> getAdminUserIds() {
        return authService.getAdminUserIds().collectList()
                .map(ids -> ResponseEntity.ok(ApiResponse.<List<Long>>builder()
                        .status("success").statusCode(200).message("Admin ids fetched successfully").data(ids).build()));
    }

    // Registers/refreshes one device's FCM token any time after login — permission is
    // granted asynchronously and tokens rotate, so this isn't limited to the deviceToken
    // captured at login/verify-otp time. A user may call this from several
    // devices/browsers at once; each becomes its own row (see DeviceTokenRequestDTO).
    @PostMapping("/device-tokens")
    public Mono<ResponseEntity<ApiResponse<Object>>> registerDeviceToken(@Valid @RequestBody DeviceTokenRequestDTO request) {
        return Mono.zip(currentAuth().map(auth -> Long.valueOf(auth.getName())), currentRole())
                .flatMap(t -> authService.registerDeviceToken(t.getT1(), t.getT2(), request.getDeviceType(), request.getFcmToken()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").statusCode(200).message("Device token registered").data(null).build()));
    }

    // Internal, no auth — see SecurityConfig. Called by zivdah-notification-service to
    // resolve every active token for a user (they may be signed in on several devices)
    // before sending a push. Empty list, not an error, when there are none.
    @GetMapping("/internal/device-tokens/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> getActiveDeviceTokens(@PathVariable Long userId) {
        return authService.getActiveDeviceTokens(userId).collectList()
                .map(tokens -> ResponseEntity.ok(ApiResponse.<List<String>>builder()
                        .status("success").statusCode(200).message("Device tokens fetched").data(tokens).build()));
    }

    // Internal, no auth — see SecurityConfig. Called by zivdah-notification-service when
    // Firebase reports a token as unregistered/invalid, so future sends stop targeting it.
    @PatchMapping("/internal/device-tokens/deactivate")
    public Mono<ResponseEntity<ApiResponse<Object>>> deactivateDeviceToken(@Valid @RequestBody DeactivateDeviceTokenDTO request) {
        return authService.deactivateDeviceToken(request.getFcmToken())
                .thenReturn(ResponseEntity.ok(ApiResponse.<Object>builder()
                        .status("success").statusCode(200).message("Device token deactivated").data(null).build()));
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

    // fcmToken is optional (see LogoutRequestDTO) — when the caller omits it, every device
    // stays registered for push; this endpoint just clears the JWT session bookkeeping.
    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Object>>> logout(@RequestBody(required = false) LogoutRequestDTO request) {
        String fcmToken = request != null ? request.getFcmToken() : null;
        return currentAuth()
                .flatMap(auth -> authService.logout(Long.valueOf(auth.getName()), fcmToken))
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
