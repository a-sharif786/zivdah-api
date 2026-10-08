package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.CreateAppVersionRequestDTO;
import com.zivdah.auth.entity.AppVersion;
import com.zivdah.auth.enums.AppPlatform;
import com.zivdah.auth.exception.ResourceConflictException;
import com.zivdah.auth.exception.ResourceNotFoundException;
import com.zivdah.auth.repository.AppVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppVersionServiceImplTest {

    private static final String PLAY_URL = "https://play.google.com/store/apps/details?id=com.zivdah.app";

    private AppVersionRepository repository;
    private AppVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AppVersionRepository.class);
        service = new AppVersionServiceImpl(repository);
    }

    private void latestAndroid(int major, int minor, int patch) {
        AppVersion latest = AppVersion.builder().id(1L).platform(AppPlatform.ANDROID)
                .version(major + "." + minor + "." + patch)
                .versionMajor(major).versionMinor(minor).versionPatch(patch)
                .storeUrl(PLAY_URL).active(true).build();
        when(repository.findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform.ANDROID))
                .thenReturn(Mono.just(latest));
    }

    @Test
    void matchingVersionIsUpToDate() {
        latestAndroid(1, 2, 0);

        StepVerifier.create(service.checkVersion("Android", "1.2"))
                .assertNext(r -> {
                    assertThat(r.isUpdateRequired()).isFalse();
                    assertThat(r.isForceUpdate()).isFalse();
                    assertThat(r.getPlatform()).isEqualTo("android");
                    assertThat(r.getCurrentVersion()).isEqualTo("1.2.0");
                    assertThat(r.getLatestVersion()).isEqualTo("1.2.0");
                    assertThat(r.getMessage()).isEqualTo(AppVersionServiceImpl.UP_TO_DATE_MESSAGE);
                })
                .verifyComplete();
        verify(repository, never()).existsActiveForcedNewerThan(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void olderVersionRequiresUpdateAndReportsForce() {
        latestAndroid(1, 10, 0);
        when(repository.existsActiveForcedNewerThan("ANDROID", 1, 9, 0)).thenReturn(Mono.just(true));

        StepVerifier.create(service.checkVersion("android", "1.9.0"))
                .assertNext(r -> {
                    assertThat(r.isUpdateRequired()).isTrue();
                    assertThat(r.isForceUpdate()).isTrue();
                    assertThat(r.getMessage()).isEqualTo("Version not match. Please update your app.");
                    assertThat(r.getStoreUrl()).isEqualTo(PLAY_URL);
                })
                .verifyComplete();
    }

    @Test
    void newerThanLatestDoesNotRequireUpdate() {
        latestAndroid(1, 2, 0);

        StepVerifier.create(service.checkVersion("android", "1.3.0"))
                .assertNext(r -> assertThat(r.isUpdateRequired()).isFalse())
                .verifyComplete();
    }

    @Test
    void noActiveReleaseFailsOpen() {
        when(repository.findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform.IOS))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.checkVersion("ios", "1.0.0"))
                .assertNext(r -> {
                    assertThat(r.isUpdateRequired()).isFalse();
                    assertThat(r.getLatestVersion()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void invalidPlatformOrVersionIsRejected() {
        assertThatThrownBy(() -> service.checkVersion("windows", "1.0.0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.checkVersion("android", "abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void latestWithNoActiveReleaseIs404() {
        when(repository.findFirstByPlatformAndActiveTrueOrderByVersionMajorDescVersionMinorDescVersionPatchDesc(AppPlatform.IOS))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.getLatestVersion("ios"))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void duplicateReleaseIs409() {
        CreateAppVersionRequestDTO req = new CreateAppVersionRequestDTO();
        req.setPlatform("android");
        req.setVersion("1.2.0");
        req.setStoreUrl(PLAY_URL);
        when(repository.findByPlatformAndVersion(AppPlatform.ANDROID, "1.2.0"))
                .thenReturn(Mono.just(AppVersion.builder().id(7L).build()));

        StepVerifier.create(service.createVersion(req))
                .expectError(ResourceConflictException.class)
                .verify();
        verify(repository, never()).save(any());
    }
}
