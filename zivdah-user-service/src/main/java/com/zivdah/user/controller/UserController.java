package com.zivdah.user.controller;

import com.zivdah.user.dto.*;
import com.zivdah.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/getProfile")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> getProfile(Authentication authentication) {
        String mobile = authentication.getName();
        return userService.getProfileByMobile(mobile)
                .map(r -> ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                        .status("success").statusCode(200).message("User fetched successfully").data(r).build()));
    }

    @PutMapping("/updateProfile")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> updateProfile(
            Authentication authentication, @Valid @RequestBody UpdateUserProfileDTO dto) {
        String mobile = authentication.getName();
        return userService.updateProfile(mobile, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                        .status("success").statusCode(200).message("Profile updated successfully").data(r).build()));
    }

    @PostMapping("/address")
    public Mono<ResponseEntity<ApiResponse<AddressResponseDTO>>> addAddress(
            Authentication authentication, @Valid @RequestBody AddressRequestDTO dto) {
        Long userId = Long.parseLong(authentication.getName());
        return userService.addAddress(userId, dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<AddressResponseDTO>builder()
                        .status("success").statusCode(201).message("Address added successfully").data(r).build()));
    }

    @GetMapping("/address")
    public Mono<ResponseEntity<ApiResponse<List<AddressResponseDTO>>>> getAddresses(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = Long.parseLong(authentication.getName());
        return userService.getAddresses(userId, PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<AddressResponseDTO>>builder()
                        .status("success").statusCode(200).message("Addresses retrieved successfully").data(list).build()));
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<ApiResponse<Void>>> resetPassword(
            Authentication authentication, @Valid @RequestBody ResetPasswordDTO dto) {
        Long userId = Long.parseLong(authentication.getName());
        return userService.resetPassword(userId, dto)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>builder()
                        .status("success").statusCode(200).message("Password reset successfully").data(null).build()));
    }

    @GetMapping("/all-users")
    public Mono<ResponseEntity<ApiResponse<List<AuthUserDTO>>>> getAllUsers() {
        return userService.getAllUsersFromAuth()
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<AuthUserDTO>>builder()
                        .status("success").statusCode(200).message("Users fetched from auth service").data(list).build()));
    }
}
