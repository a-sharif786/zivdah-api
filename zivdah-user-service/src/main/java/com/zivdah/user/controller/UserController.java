package com.zivdah.user.controller;

import com.zivdah.user.dto.*;
import com.zivdah.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> getProfile(ServerHttpRequest request) {

        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .doOnNext(auth -> {
                    log.info("Authentication: {}", auth);
                    log.info("Name: {}", auth.getName());
                })
                .flatMap(auth ->
                        userService.getProfileByUserId(Long.valueOf(auth.getName()), token)
                )
                .map(r -> ResponseEntity.ok(
                        ApiResponse.<UserResponseDTO>builder()
                                .status("success")
                                .statusCode(200)
                                .message("User fetched successfully")
                                .data(r)
                                .build()
                ));


    }


    @PutMapping("/updateProfile")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UserResponseDTO>>> updateProfile(
            Authentication authentication,
            ServerHttpRequest request,
            @Valid @RequestBody UpdateUserProfileDTO dto) {

        String mobile = authentication.getName();
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return userService.updateProfile(mobile, dto, token)
                .map(r -> ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                        .status("success")
                        .statusCode(200)
                        .message("Profile updated successfully")
                        .data(r)
                        .build()));
    }


    @PostMapping("/address")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AddressResponseDTO>>> addAddress(
            Authentication authentication,
            ServerHttpRequest request,
            @Valid @RequestBody AddressRequestDTO dto) {

        Long userId = Long.parseLong(authentication.getName());
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return userService.addAddress(userId, dto, token)
                .map(r -> ResponseEntity.ok(ApiResponse.<AddressResponseDTO>builder()
                        .status("success")
                        .statusCode(201)
                        .message("Address added successfully")
                        .data(r)
                        .build()));
    }


    @GetMapping("/address")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<AddressResponseDTO>>>> getAddresses(
            Authentication authentication,
            ServerHttpRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = Long.parseLong(authentication.getName());



        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        return userService.getAddresses(userId, PageRequest.of(page, size), token)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<AddressResponseDTO>>builder()
                        .status("success")
                        .statusCode(200)
                        .message("Addresses retrieved successfully")
                        .data(list)
                        .build()));
    }

    /**
     * ADMIN ONLY
     * Get addresses by userId
     */
    @GetMapping("/address/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<AddressResponseDTO>>>> getAddressByUserId(
            @PathVariable Long userId,
            ServerHttpRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return userService.getAddressesByUserId(
                        userId,
                        PageRequest.of(page, size), token
                )

                .collectList()

                .map(list ->
                        ResponseEntity.ok(
                                ApiResponse.<List<AddressResponseDTO>>builder()
                                        .status("success")
                                        .statusCode(200)
                                        .message("User addresses retrieved successfully")
                                        .data(list)
                                        .build()
                        )
                );
    }




}