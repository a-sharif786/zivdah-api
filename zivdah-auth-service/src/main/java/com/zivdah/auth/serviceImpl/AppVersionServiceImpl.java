package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.AppVersion;
import com.zivdah.auth.enums.AppPlatform;
import com.zivdah.auth.exception.ResourceConflictException;
import com.zivdah.auth.exception.ResourceNotFoundException;
import com.zivdah.auth.repository.AppVersionRepository;
import com.zivdah.auth.service.AppVersionService;
import com.zivdah.auth.util.SemanticVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppVersionServiceImpl implements AppVersionService {

    static final String UPDATE_REQUIRED_MESSAGE = "Version not match. Please update your app.";
    static final String UP_TO_DATE_MESSAGE = "App is up to date.";
    static final String NOT_CONFIGURED_MESSAGE = "No released version is configured for this platform.";

    private final AppVersionRepository appVersionRepository;

    // An installed version newer than the latest active release (a release was disabled after
    // rollout, or a store build is ahead of the config) is NOT told to "update" — there is
    // nothing newer to update to. With no active release at all, the check fails open
    // (updateRequired=false) so a missing config row can never lock every user out of the app.
    @Override
    public Mono<VersionCheckResponseDTO> checkVersion(String platform, String currentVersion) {
        AppPlatform appPlatform = AppPlatform.from(platform);
        SemanticVersion current = SemanticVersion.parse(currentVersion);

        return appVersionRepository
                .findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(appPlatform)
                .flatMap(latest -> {
                    SemanticVersion latestVersion = toSemver(latest);
                    boolean updateRequired = current.compareTo(latestVersion) < 0;
                    Mono<Boolean> forced = updateRequired
                            ? appVersionRepository.existsActiveForcedNewerThan(
                                    appPlatform.name(), current.major(), current.minor(), current.patch())
                            : Mono.just(false);
                    return forced.map(forceUpdate -> VersionCheckResponseDTO.builder()
                            .platform(appPlatform.apiValue())
                            .currentVersion(current.toString())
                            .latestVersion(latest.getVersion())
                            .updateRequired(updateRequired)
                            .forceUpdate(forceUpdate)
                            .message(updateRequired ? UPDATE_REQUIRED_MESSAGE : UP_TO_DATE_MESSAGE)
                            .storeUrl(latest.getStoreUrl())
                            .build());
                })
                .switchIfEmpty(Mono.fromSupplier(() -> VersionCheckResponseDTO.builder()
                        .platform(appPlatform.apiValue())
                        .currentVersion(current.toString())
                        .latestVersion(null)
                        .updateRequired(false)
                        .forceUpdate(false)
                        .message(NOT_CONFIGURED_MESSAGE)
                        .storeUrl(null)
                        .build()));
    }

    @Override
    public Mono<AppVersionResponseDTO> getLatestVersion(String platform) {
        AppPlatform appPlatform = AppPlatform.from(platform);
        return appVersionRepository
                .findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(appPlatform)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "No active version found for platform " + appPlatform.apiValue())))
                .map(AppVersionResponseDTO::from);
    }

    @Override
    public Mono<AppVersionResponseDTO> createVersion(CreateAppVersionRequestDTO request) {
        AppPlatform appPlatform = AppPlatform.from(request.getPlatform());
        SemanticVersion version = SemanticVersion.parse(request.getVersion());
        LocalDateTime now = LocalDateTime.now();

        AppVersion entity = AppVersion.builder()
                .platform(appPlatform)
                .version(version.toString())
                .versionMajor(version.major())
                .versionMinor(version.minor())
                .versionPatch(version.patch())
                .storeUrl(request.getStoreUrl().trim())
                .forceUpdate(request.isForceUpdate())
                .active(request.isActive())
                .releaseNotes(request.getReleaseNotes())
                .releasedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return ensureVersionFree(appPlatform, version, null)
                .then(Mono.defer(() -> appVersionRepository.save(entity)))
                .onErrorMap(DataIntegrityViolationException.class, ex -> duplicate(appPlatform, version))
                .doOnNext(saved -> log.info("Released {} app version {} (id={}, forceUpdate={}, active={})",
                        appPlatform.apiValue(), saved.getVersion(), saved.getId(), saved.isForceUpdate(), saved.isActive()))
                .map(AppVersionResponseDTO::from);
    }

    @Override
    public Mono<AppVersionResponseDTO> updateVersion(Long id, UpdateAppVersionRequestDTO request) {
        SemanticVersion version = SemanticVersion.parse(request.getVersion());

        return findOrThrow(id)
                .flatMap(existing -> ensureVersionFree(existing.getPlatform(), version, id)
                        .then(Mono.defer(() -> {
                            existing.setVersion(version.toString());
                            existing.setVersionMajor(version.major());
                            existing.setVersionMinor(version.minor());
                            existing.setVersionPatch(version.patch());
                            existing.setStoreUrl(request.getStoreUrl().trim());
                            existing.setForceUpdate(request.getForceUpdate());
                            existing.setReleaseNotes(request.getReleaseNotes());
                            existing.setUpdatedAt(LocalDateTime.now());
                            return appVersionRepository.save(existing);
                        }))
                        .onErrorMap(DataIntegrityViolationException.class, ex -> duplicate(existing.getPlatform(), version)))
                .doOnNext(saved -> log.info("Updated app version id={} -> {} {}", id, saved.getPlatform().apiValue(), saved.getVersion()))
                .map(AppVersionResponseDTO::from);
    }

    @Override
    public Mono<AppVersionResponseDTO> updateStatus(Long id, boolean active) {
        return findOrThrow(id)
                .flatMap(existing -> {
                    existing.setActive(active);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return appVersionRepository.save(existing);
                })
                .doOnNext(saved -> log.info("{} {} app version {} (id={})", active ? "Enabled" : "Disabled",
                        saved.getPlatform().apiValue(), saved.getVersion(), id))
                .map(AppVersionResponseDTO::from);
    }

    @Override
    public Mono<AppVersionResponseDTO> getVersion(Long id) {
        return findOrThrow(id).map(AppVersionResponseDTO::from);
    }

    @Override
    public Flux<AppVersionResponseDTO> getVersions(String platform) {
        Flux<AppVersion> versions = (platform == null || platform.isBlank())
                ? appVersionRepository.findAllByOrderByPlatformAscVersionMajorDescVersionMinorDescVersionPatchDesc()
                : appVersionRepository.findByPlatformOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform.from(platform));
        return versions.map(AppVersionResponseDTO::from);
    }

    private Mono<AppVersion> findOrThrow(Long id) {
        return appVersionRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("App version not found with id " + id)));
    }

    // Friendly 409 for the common case; the UNIQUE(platform, version) constraint still backs it
    // up against two concurrent creates (mapped from DataIntegrityViolationException above).
    private Mono<Void> ensureVersionFree(AppPlatform platform, SemanticVersion version, Long excludeId) {
        return appVersionRepository.findByPlatformAndVersion(platform, version.toString())
                .filter(found -> !found.getId().equals(excludeId))
                .flatMap(found -> Mono.<Void>error(duplicate(platform, version)));
    }

    private static ResourceConflictException duplicate(AppPlatform platform, SemanticVersion version) {
        return new ResourceConflictException(
                "Version " + version + " already exists for platform " + platform.apiValue());
    }

    private static SemanticVersion toSemver(AppVersion v) {
        return new SemanticVersion(v.getVersionMajor(), v.getVersionMinor(), v.getVersionPatch());
    }
}
