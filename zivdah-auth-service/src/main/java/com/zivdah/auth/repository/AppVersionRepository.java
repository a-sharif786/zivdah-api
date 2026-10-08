package com.zivdah.auth.repository;

import com.zivdah.auth.entity.AppVersion;
import com.zivdah.auth.enums.AppPlatform;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AppVersionRepository extends ReactiveCrudRepository<AppVersion, Long> {

    // Highest active semantic version — the "latest" release clients are compared against.
    Mono<AppVersion> findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform platform);

    Flux<AppVersion> findByPlatformOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform platform);

    Flux<AppVersion> findAllByOrderByPlatformAscVersionMajorDescVersionMinorDescVersionPatchDesc();

    Mono<AppVersion> findByPlatformAndVersion(AppPlatform platform, String version);

    // Force-update applies if ANY active release newer than the installed one is marked forced,
    // not just the latest — skipping a forced 1.1.0 by way of an optional 1.2.0 must still force.
    @Query("SELECT EXISTS (SELECT 1 FROM app_versions WHERE platform = :platform AND active AND force_update " +
            "AND (version_major, version_minor, version_patch) > (:major, :minor, :patch))")
    Mono<Boolean> existsActiveForcedNewerThan(String platform, int major, int minor, int patch);
}
