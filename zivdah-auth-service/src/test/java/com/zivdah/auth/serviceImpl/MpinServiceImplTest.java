package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.LoginResponseDTO;
import com.zivdah.auth.dto.MpinChangeRequestDTO;
import com.zivdah.auth.dto.MpinLoginRequestDTO;
import com.zivdah.auth.dto.MpinSetupRequestDTO;
import com.zivdah.auth.entity.MpinDevice;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.exception.MpinException;
import com.zivdah.auth.repository.MpinDeviceRepository;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.security.LoginResponseFactory;
import com.zivdah.auth.security.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MpinServiceImplTest {

    private static final String MOBILE = "9876543210";
    private static final String DEVICE_ID = "device-0001";
    private static final String SECRET = "the-device-secret";
    private static final String PIN = "482917";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private MpinDeviceRepository devices;
    private UserRepository users;
    private LoginResponseFactory loginResponses;
    private MpinServiceImpl service;
    private UserEntity user;
    private MpinDevice device;

    @BeforeEach
    void setUp() {
        devices = mock(MpinDeviceRepository.class);
        users = mock(UserRepository.class);
        loginResponses = mock(LoginResponseFactory.class);
        service = new MpinServiceImpl(devices, users, encoder, loginResponses);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "setupWindow", Duration.ofMinutes(10));

        user = UserEntity.builder().id(7L).mobile(MOBILE).role(Role.USER).active(true).build();
        device = MpinDevice.builder().id(70L).userId(7L).deviceId(DEVICE_ID)
                .deviceSecretHash(SecureTokens.sha256Hex(SECRET)).mpinHash(encoder.encode(PIN)).build();

        when(users.findByMobile(MOBILE)).thenReturn(Mono.just(user));
        when(users.findById(7L)).thenReturn(Mono.just(user));
        when(devices.findByUserIdAndDeviceId(7L, DEVICE_ID)).thenReturn(Mono.just(device));
        when(devices.markSuccess(eq(70L), any())).thenReturn(Mono.just(1));
        when(loginResponses.issue(any(), any())).thenReturn(Mono.just(LoginResponseDTO.builder().id(7L).token("jwt").build()));
    }

    private static MpinLoginRequestDTO login(String secret, String pin) {
        MpinLoginRequestDTO req = new MpinLoginRequestDTO();
        req.setMobile(MOBILE);
        req.setDeviceId(DEVICE_ID);
        req.setDeviceSecret(secret);
        req.setMpin(pin);
        return req;
    }

    private static void assertMpinError(Throwable e, HttpStatus status, String messageStart) {
        assertThat(e).isInstanceOf(MpinException.class);
        assertThat(((MpinException) e).getStatus()).isEqualTo(status);
        assertThat(e.getMessage()).startsWith(messageStart);
    }

    @Test
    void correctSecretAndPinLogsInWithoutAuthTime() {
        when(devices.reserveAttempt(70L, 5)).thenReturn(Mono.just(1));

        StepVerifier.create(service.login(login(SECRET, PIN)))
                .assertNext(resp -> assertThat(resp.getToken()).isEqualTo("jwt"))
                .verifyComplete();
        verify(devices).markSuccess(eq(70L), any());
        // auth_time null: an MPIN login must not count as a fresh full login
        verify(loginResponses).issue(user, null);
    }

    @Test
    void wrongSecretIsGenericAndNeverConsumesAnAttempt() {
        StepVerifier.create(service.login(login("not-the-secret", PIN)))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.UNAUTHORIZED, MpinServiceImpl.INVALID_MPIN));
        verify(devices, never()).reserveAttempt(anyLong(), anyInt());
    }

    @Test
    void unknownMobileAndRevokedDeviceAreGeneric() {
        when(users.findByMobile("1111111111")).thenReturn(Mono.empty());
        MpinLoginRequestDTO unknown = login(SECRET, PIN);
        unknown.setMobile("1111111111");
        StepVerifier.create(service.login(unknown))
                .verifyErrorSatisfies(e -> assertThat(e.getMessage()).isEqualTo(MpinServiceImpl.INVALID_MPIN));

        device.setRevokedAt(LocalDateTime.now());
        StepVerifier.create(service.login(login(SECRET, PIN)))
                .verifyErrorSatisfies(e -> assertThat(e.getMessage()).isEqualTo(MpinServiceImpl.INVALID_MPIN));
        verify(devices, never()).reserveAttempt(anyLong(), anyInt());
    }

    @Test
    void adminAccountsCannotUseMpin() {
        user.setRole(Role.ADMIN);
        StepVerifier.create(service.login(login(SECRET, PIN)))
                .verifyErrorSatisfies(e -> assertThat(e.getMessage()).isEqualTo(MpinServiceImpl.INVALID_MPIN));
    }

    @Test
    void wrongPinReportsRemainingAttempts() {
        when(devices.reserveAttempt(70L, 5)).thenReturn(Mono.just(1));
        when(devices.revokeIfExhausted(eq(70L), eq(5), any())).thenReturn(Mono.just(0));
        when(devices.findById(70L)).thenReturn(Mono.just(MpinDevice.builder().failedAttempts(2).build()));

        StepVerifier.create(service.login(login(SECRET, "000001")))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.UNAUTHORIZED, "Invalid MPIN. 3 attempts remaining."));
        verify(loginResponses, never()).issue(any(), any());
    }

    @Test
    void fifthWrongPinLocksTheDevice() {
        when(devices.reserveAttempt(70L, 5)).thenReturn(Mono.just(1));
        when(devices.revokeIfExhausted(eq(70L), eq(5), any())).thenReturn(Mono.just(1));

        StepVerifier.create(service.login(login(SECRET, "000001")))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.UNAUTHORIZED, MpinServiceImpl.LOCKED));
    }

    @Test
    void noAttemptLeftMeansThePinIsNeverCompared() {
        when(devices.reserveAttempt(70L, 5)).thenReturn(Mono.just(0));

        // even the CORRECT pin is refused once the attempts are used up
        StepVerifier.create(service.login(login(SECRET, PIN)))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.UNAUTHORIZED, MpinServiceImpl.LOCKED));
        verify(loginResponses, never()).issue(any(), any());
    }

    private static MpinSetupRequestDTO setupRequest(String pin) {
        MpinSetupRequestDTO req = new MpinSetupRequestDTO();
        req.setDeviceId(DEVICE_ID);
        req.setDeviceName("Pixel 8");
        req.setMpin(pin);
        return req;
    }

    @Test
    void setupRequiresARecentFullLogin() {
        StepVerifier.create(service.setup(7L, null, setupRequest(PIN)))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.FORBIDDEN, MpinServiceImpl.SETUP_NEEDS_FRESH_LOGIN));
        StepVerifier.create(service.setup(7L, Instant.now().minus(Duration.ofMinutes(11)), setupRequest(PIN)))
                .verifyErrorSatisfies(e -> assertMpinError(e, HttpStatus.FORBIDDEN, MpinServiceImpl.SETUP_NEEDS_FRESH_LOGIN));
        verify(devices, never()).save(any());
    }

    @Test
    void setupReturnsASecretAndStoresOnlyHashes() {
        device.setRevokedAt(LocalDateTime.now());
        device.setFailedAttempts(5);
        when(devices.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.setup(7L, Instant.now().minusSeconds(30), setupRequest("361850")))
                .assertNext(resp -> {
                    assertThat(resp.getDeviceId()).isEqualTo(DEVICE_ID);
                    assertThat(resp.getDeviceSecret()).hasSizeGreaterThanOrEqualTo(43);
                    assertThat(device.getDeviceSecretHash()).isEqualTo(SecureTokens.sha256Hex(resp.getDeviceSecret()));
                    assertThat(device.getMpinHash()).isNotEqualTo("361850");
                    assertThat(encoder.matches("361850", device.getMpinHash())).isTrue();
                    // re-setup clears the previous lockout
                    assertThat(device.getRevokedAt()).isNull();
                    assertThat(device.getFailedAttempts()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void weakPinsAreRejected() {
        for (String weak : new String[]{"000000", "111111", "123456", "012345", "987654", "654321"}) {
            assertThatThrownBy(() -> MpinServiceImpl.validateStrength(weak)).isInstanceOf(IllegalArgumentException.class);
        }
        MpinServiceImpl.validateStrength("482917");
        MpinServiceImpl.validateStrength("112233");
    }

    @Test
    void changeVerifiesOldPinThenStoresNewHash() {
        when(devices.reserveAttempt(70L, 5)).thenReturn(Mono.just(1));
        when(devices.changeMpin(eq(70L), anyString(), any())).thenReturn(Mono.just(1));
        MpinChangeRequestDTO req = new MpinChangeRequestDTO();
        req.setDeviceId(DEVICE_ID);
        req.setOldMpin(PIN);
        req.setNewMpin("590372");

        StepVerifier.create(service.change(7L, req)).verifyComplete();
        verify(devices).changeMpin(eq(70L), argThat(hash -> encoder.matches("590372", hash)), any());
    }
}
