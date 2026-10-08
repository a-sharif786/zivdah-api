package com.zivdah.auth.controller;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.security.JwtTokenProvider;
import com.zivdah.auth.service.MpinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

// MPIN (mobile PIN) quick login — see MpinServiceImpl for the security model.
// /login is public (see SecurityConfig); everything else needs the user's JWT.
@RestController
@RequestMapping("/restful/v1/api/auth/mpin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MpinController {

    private final MpinService mpinService;
    private final JwtTokenProvider jwtTokenProvider;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> Long.valueOf(ctx.getAuthentication().getName()))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Not authenticated")));
    }

    // Right after a password/OTP login (the access token's auth_time must be recent). Returns
    // the device secret exactly once.
    @PostMapping("/setup")
    public Mono<ResponseEntity<ApiResponse<MpinSetupResponseDTO>>> setup(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody MpinSetupRequestDTO request) {
        Instant authTime = jwtTokenProvider.getAuthTimeFromToken(authorization.substring("Bearer ".length()));
        return currentUserId()
                .flatMap(userId -> mpinService.setup(userId, authTime, request))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<MpinSetupResponseDTO>builder()
                        .status("success").statusCode(201).message("MPIN set up successfully").data(result).build()));
    }

    // Same response shape as /auth/login.
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<LoginResponseDTO>>> login(@Valid @RequestBody MpinLoginRequestDTO request) {
        return mpinService.login(request)
                .map(loginResp -> ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                        .status("success").statusCode(200).message("Login successful").data(loginResp).build()));
    }

    @PostMapping("/change")
    public Mono<ResponseEntity<ApiResponse<Object>>> change(@Valid @RequestBody MpinChangeRequestDTO request) {
        return currentUserId()
                .flatMap(userId -> mpinService.change(userId, request))
                .thenReturn(ResponseEntity.ok(ApiResponse.builder()
                        .status("success").statusCode(200).message("MPIN changed successfully").data(null).build()));
    }

    @GetMapping("/devices")
    public Mono<ResponseEntity<ApiResponse<List<MpinDeviceResponseDTO>>>> getDevices() {
        return currentUserId()
                .flatMap(userId -> mpinService.getDevices(userId).collectList())
                .map(devices -> ResponseEntity.ok(ApiResponse.<List<MpinDeviceResponseDTO>>builder()
                        .status("success").statusCode(200).message("MPIN devices fetched successfully").data(devices).build()));
    }

    @DeleteMapping("/devices/{deviceId}")
    public Mono<ResponseEntity<ApiResponse<Object>>> revokeDevice(@PathVariable String deviceId) {
        return currentUserId()
                .flatMap(userId -> mpinService.revokeDevice(userId, deviceId))
                .thenReturn(ResponseEntity.ok(ApiResponse.builder()
                        .status("success").statusCode(200).message("MPIN removed from device").data(null).build()));
    }
}
