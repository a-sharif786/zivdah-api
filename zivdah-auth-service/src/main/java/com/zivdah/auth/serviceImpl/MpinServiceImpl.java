package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.*;
import com.zivdah.auth.entity.MpinDevice;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.enums.Role;
import com.zivdah.auth.exception.MpinException;
import com.zivdah.auth.exception.ResourceConflictException;
import com.zivdah.auth.exception.ResourceNotFoundException;
import com.zivdah.auth.repository.MpinDeviceRepository;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.security.LoginResponseFactory;
import com.zivdah.auth.security.SecureTokens;
import com.zivdah.auth.service.MpinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

// MPIN = quick re-login on a phone that already did a full (password/OTP) login. A login needs
// BOTH the device secret issued at setup (256-bit, kept in Keystore/Keychain) AND the 6-digit
// PIN — the PIN alone is only 10^6 values. Every attempt is reserved against the device's
// failed_attempts BEFORE the PIN is compared, and reaching maxAttempts revokes the device's
// MPIN outright (full login + setup again), so a stolen phone gets at most maxAttempts guesses.
//
// Failures that only someone WITHOUT the device secret can hit (unknown mobile/device, wrong
// secret, inactive account) all return the same "Invalid MPIN" and never touch the counter —
// otherwise anyone knowing a deviceId could lock its owner out. Remaining-attempts / locked
// messages are only ever shown to a caller who presented the correct secret.
@Service
@Slf4j
@RequiredArgsConstructor
public class MpinServiceImpl implements MpinService {

    static final String INVALID_MPIN = "Invalid MPIN";
    static final String LOCKED = "MPIN locked after too many wrong attempts. Please log in with OTP or password and set up MPIN again.";
    static final String SETUP_NEEDS_FRESH_LOGIN = "For security, log in again with OTP or password before setting up MPIN.";

    // Mobile-app roles only — admin/vendor accounts keep full password login.
    private static final Set<Role> MPIN_ROLES = Set.of(Role.USER, Role.DELIVERY_BOY);

    private final MpinDeviceRepository mpinDeviceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginResponseFactory loginResponseFactory;

    @Value("${mpin.max-attempts:5}")
    private int maxAttempts;

    // How recent the full login (the access token's auth_time) must be for /setup.
    @Value("${mpin.setup-window:10m}")
    private Duration setupWindow;

    @Override
    public Mono<MpinSetupResponseDTO> setup(Long userId, Instant authTime, MpinSetupRequestDTO request) {
        if (authTime == null || authTime.isBefore(Instant.now().minus(setupWindow))) {
            return Mono.error(new MpinException(HttpStatus.FORBIDDEN, SETUP_NEEDS_FRESH_LOGIN));
        }
        validateStrength(request.getMpin());
        String deviceSecret = SecureTokens.randomToken();

        return userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .filter(user -> MPIN_ROLES.contains(user.getRole()))
                .switchIfEmpty(Mono.error(new MpinException(HttpStatus.FORBIDDEN, "MPIN is not available for this account")))
                .flatMap(user -> encode(request.getMpin()))
                .flatMap(mpinHash -> mpinDeviceRepository.findByUserIdAndDeviceId(userId, request.getDeviceId())
                        .defaultIfEmpty(MpinDevice.builder()
                                .userId(userId)
                                .deviceId(request.getDeviceId())
                                .createdAt(LocalDateTime.now())
                                .build())
                        .flatMap(device -> {
                            // (Re)setup on the same device replaces the secret, PIN and lockout state.
                            LocalDateTime now = LocalDateTime.now();
                            device.setDeviceName(request.getDeviceName());
                            device.setDeviceSecretHash(SecureTokens.sha256Hex(deviceSecret));
                            device.setMpinHash(mpinHash);
                            device.setFailedAttempts(0);
                            device.setRevokedAt(null);
                            device.setUpdatedAt(now);
                            if (device.getId() != null) { // re-setup: createdAt = when THIS MPIN was set
                                device.setCreatedAt(now);
                                device.setLastUsedAt(null);
                            }
                            return mpinDeviceRepository.save(device);
                        }))
                .onErrorMap(DataIntegrityViolationException.class,
                        ex -> new ResourceConflictException("MPIN setup for this device is already in progress — please retry"))
                .doOnNext(saved -> log.info("MPIN set up for userId={} (device row id={})", userId, saved.getId()))
                .map(saved -> MpinSetupResponseDTO.builder()
                        .deviceId(saved.getDeviceId())
                        .deviceSecret(deviceSecret)
                        .maxAttempts(maxAttempts)
                        .build());
    }

    @Override
    public Mono<LoginResponseDTO> login(MpinLoginRequestDTO request) {
        return userRepository.findByMobile(request.getMobile().trim())
                .filter(UserEntity::isActive)
                .filter(user -> MPIN_ROLES.contains(user.getRole()))
                .flatMap(user -> mpinDeviceRepository.findByUserIdAndDeviceId(user.getId(), request.getDeviceId())
                        .filter(device -> device.getRevokedAt() == null)
                        .filter(device -> SecureTokens.hashMatches(request.getDeviceSecret(), device.getDeviceSecretHash()))
                        .flatMap(device -> verifyPin(device, request.getMpin())
                                .then(Mono.defer(() -> mpinDeviceRepository.markSuccess(device.getId(), LocalDateTime.now())))
                                // auth_time null: an MPIN login is not a full login, so it can't
                                // be used to set up MPIN on another device.
                                .then(Mono.defer(() -> loginResponseFactory.issue(user, null)))
                                .doOnNext(resp -> log.info("MPIN login succeeded for userId={}", user.getId()))))
                .switchIfEmpty(Mono.error(() -> new MpinException(HttpStatus.UNAUTHORIZED, INVALID_MPIN)));
    }

    @Override
    public Mono<Void> change(Long userId, MpinChangeRequestDTO request) {
        if (request.getOldMpin().equals(request.getNewMpin())) {
            return Mono.error(new IllegalArgumentException("New MPIN must be different from the current MPIN"));
        }
        validateStrength(request.getNewMpin());

        return activeDevice(userId, request.getDeviceId())
                .flatMap(device -> verifyPin(device, request.getOldMpin())
                        .then(Mono.defer(() -> encode(request.getNewMpin())))
                        .flatMap(newHash -> mpinDeviceRepository.changeMpin(device.getId(), newHash, LocalDateTime.now()))
                        .flatMap(updated -> updated == 0
                                ? Mono.<Void>error(new MpinException(HttpStatus.UNAUTHORIZED, LOCKED))
                                : Mono.<Void>empty()))
                .doOnSuccess(v -> log.info("MPIN changed for userId={}", userId));
    }

    @Override
    public Flux<MpinDeviceResponseDTO> getDevices(Long userId) {
        return mpinDeviceRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId)
                .map(MpinDeviceResponseDTO::from);
    }

    @Override
    public Mono<Void> revokeDevice(Long userId, String deviceId) {
        return mpinDeviceRepository.revokeDevice(userId, deviceId, LocalDateTime.now())
                .flatMap(updated -> updated == 0
                        ? Mono.<Void>error(new ResourceNotFoundException("MPIN is not set up on this device"))
                        : Mono.<Void>empty())
                .doOnSuccess(v -> log.info("MPIN revoked on one device for userId={}", userId));
    }

    private Mono<MpinDevice> activeDevice(Long userId, String deviceId) {
        return mpinDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .filter(device -> device.getRevokedAt() == null)
                .switchIfEmpty(Mono.error(() -> new ResourceNotFoundException("MPIN is not set up on this device")));
    }

    // Completes empty if the PIN is right, errors otherwise. The attempt is reserved first: once
    // failed_attempts hits the max, reserveAttempt returns 0 and the PIN is never compared.
    private Mono<Void> verifyPin(MpinDevice device, String mpin) {
        return mpinDeviceRepository.reserveAttempt(device.getId(), maxAttempts)
                .flatMap(reserved -> reserved == 0
                        ? Mono.<Boolean>error(new MpinException(HttpStatus.UNAUTHORIZED, LOCKED))
                        : matches(mpin, device.getMpinHash()))
                .flatMap(ok -> ok ? Mono.<Void>empty() : onWrongPin(device.getId()));
    }

    private Mono<Void> onWrongPin(Long deviceRowId) {
        return mpinDeviceRepository.revokeIfExhausted(deviceRowId, maxAttempts, LocalDateTime.now())
                .flatMap(justLocked -> {
                    if (justLocked > 0) {
                        log.warn("MPIN locked after {} wrong attempts (device row id={})", maxAttempts, deviceRowId);
                        return Mono.<Void>error(new MpinException(HttpStatus.UNAUTHORIZED, LOCKED));
                    }
                    return mpinDeviceRepository.findById(deviceRowId)
                            .map(d -> Math.max(0, maxAttempts - d.getFailedAttempts()))
                            .defaultIfEmpty(0)
                            .flatMap(remaining -> Mono.<Void>error(new MpinException(HttpStatus.UNAUTHORIZED,
                                    remaining == 0 ? LOCKED
                                            : INVALID_MPIN + ". " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining.")));
                });
    }

    private Mono<Boolean> matches(String mpin, String mpinHash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(mpin, mpinHash))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> encode(String mpin) {
        return Mono.fromCallable(() -> passwordEncoder.encode(mpin))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Rejects 000000/111111..., and straight runs up or down (012345, 123456, 987654, ...).
    static void validateStrength(String mpin) {
        if (mpin == null || !mpin.matches("\\d{6}")) {
            throw new IllegalArgumentException("MPIN must be exactly 6 digits");
        }
        boolean allSame = true, ascending = true, descending = true;
        for (int i = 1; i < mpin.length(); i++) {
            int prev = mpin.charAt(i - 1), cur = mpin.charAt(i);
            allSame &= cur == prev;
            ascending &= cur == prev + 1;
            descending &= cur == prev - 1;
        }
        if (allSame || ascending || descending) {
            throw new IllegalArgumentException("MPIN is too easy to guess — avoid repeated or sequential digits");
        }
    }
}
