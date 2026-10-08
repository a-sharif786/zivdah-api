package com.zivdah.auth.controller;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.service.AppVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

// Mobile app version control, managed separately per platform (android / ios).
// /check and /latest are public — the app calls them at launch, before any login (see
// SecurityConfig). Everything else is admin-only release management.
@RestController
@RequestMapping("/restful/v1/api/auth/app-versions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AppVersionController {

    private final AppVersionService appVersionService;

    @GetMapping("/check")
    public Mono<ResponseEntity<ApiResponse<VersionCheckResponseDTO>>> checkVersion(
            @RequestParam String platform, @RequestParam String currentVersion) {
        return appVersionService.checkVersion(platform, currentVersion)
                .map(result -> ResponseEntity.ok(ApiResponse.<VersionCheckResponseDTO>builder()
                        .status("success").statusCode(200).message(result.getMessage()).data(result).build()));
    }

    @GetMapping("/latest")
    public Mono<ResponseEntity<ApiResponse<AppVersionResponseDTO>>> getLatestVersion(@RequestParam String platform) {
        return appVersionService.getLatestVersion(platform)
                .map(version -> ResponseEntity.ok(ApiResponse.<AppVersionResponseDTO>builder()
                        .status("success").statusCode(200).message("Latest version fetched successfully").data(version).build()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppVersionResponseDTO>>> createVersion(
            @Valid @RequestBody CreateAppVersionRequestDTO request) {
        return appVersionService.createVersion(request)
                .map(version -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AppVersionResponseDTO>builder()
                        .status("success").statusCode(201).message("App version released successfully").data(version).build()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppVersionResponseDTO>>> updateVersion(
            @PathVariable Long id, @Valid @RequestBody UpdateAppVersionRequestDTO request) {
        return appVersionService.updateVersion(id, request)
                .map(version -> ResponseEntity.ok(ApiResponse.<AppVersionResponseDTO>builder()
                        .status("success").statusCode(200).message("App version updated successfully").data(version).build()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppVersionResponseDTO>>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AppVersionStatusRequestDTO request) {
        return appVersionService.updateStatus(id, request.getActive())
                .map(version -> ResponseEntity.ok(ApiResponse.<AppVersionResponseDTO>builder()
                        .status("success").statusCode(200)
                        .message(version.isActive() ? "App version enabled" : "App version disabled")
                        .data(version).build()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AppVersionResponseDTO>>> getVersion(@PathVariable Long id) {
        return appVersionService.getVersion(id)
                .map(version -> ResponseEntity.ok(ApiResponse.<AppVersionResponseDTO>builder()
                        .status("success").statusCode(200).message("App version fetched successfully").data(version).build()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<AppVersionResponseDTO>>>> getVersions(
            @RequestParam(required = false) String platform) {
        return appVersionService.getVersions(platform).collectList()
                .map(versions -> ResponseEntity.ok(ApiResponse.<List<AppVersionResponseDTO>>builder()
                        .status("success").statusCode(200).message("App versions fetched successfully").data(versions).build()));
    }
}
