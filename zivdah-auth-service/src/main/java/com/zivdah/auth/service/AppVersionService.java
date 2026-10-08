package com.zivdah.auth.service;

import com.zivdah.auth.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AppVersionService {
    Mono<VersionCheckResponseDTO> checkVersion(String platform, String currentVersion);
    Mono<AppVersionResponseDTO> getLatestVersion(String platform);
    Mono<AppVersionResponseDTO> createVersion(CreateAppVersionRequestDTO request);
    Mono<AppVersionResponseDTO> updateVersion(Long id, UpdateAppVersionRequestDTO request);
    Mono<AppVersionResponseDTO> updateStatus(Long id, boolean active);
    Mono<AppVersionResponseDTO> getVersion(Long id);
    Flux<AppVersionResponseDTO> getVersions(String platform);
}
