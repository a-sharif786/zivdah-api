package com.zivdah.auth.repository;

import com.zivdah.auth.entity.MpinDevice;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface MpinDeviceRepository extends ReactiveCrudRepository<MpinDevice, Long> {

    // Includes revoked rows — setup reuses them (UNIQUE(user_id, device_id)).
    Mono<MpinDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    Flux<MpinDevice> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    // Reserves one attempt BEFORE the PIN is checked. Atomic and conditional, so N concurrent
    // guesses get at most (max - failed_attempts) 1s back — the rest see 0 and are refused
    // without their PIN ever being compared.
    @Modifying
    @Query("UPDATE mpin_devices SET failed_attempts = failed_attempts + 1 " +
            "WHERE id = :id AND revoked_at IS NULL AND failed_attempts < :max")
    Mono<Integer> reserveAttempt(Long id, int max);

    @Modifying
    @Query("UPDATE mpin_devices SET failed_attempts = 0, last_used_at = :now WHERE id = :id")
    Mono<Integer> markSuccess(Long id, LocalDateTime now);

    // 1 = this failure exhausted the attempts and just locked the device.
    @Modifying
    @Query("UPDATE mpin_devices SET revoked_at = :now, updated_at = :now " +
            "WHERE id = :id AND revoked_at IS NULL AND failed_attempts >= :max")
    Mono<Integer> revokeIfExhausted(Long id, int max, LocalDateTime now);

    @Modifying
    @Query("UPDATE mpin_devices SET mpin_hash = :mpinHash, failed_attempts = 0, updated_at = :now " +
            "WHERE id = :id AND revoked_at IS NULL")
    Mono<Integer> changeMpin(Long id, String mpinHash, LocalDateTime now);

    @Modifying
    @Query("UPDATE mpin_devices SET revoked_at = :now, updated_at = :now " +
            "WHERE user_id = :userId AND device_id = :deviceId AND revoked_at IS NULL")
    Mono<Integer> revokeDevice(Long userId, String deviceId, LocalDateTime now);

    // Password reset, deactivation and logout-from-all-devices: an old PIN must not outlive them.
    @Modifying
    @Query("UPDATE mpin_devices SET revoked_at = :now, updated_at = :now WHERE user_id = :userId AND revoked_at IS NULL")
    Mono<Integer> revokeAllForUser(Long userId, LocalDateTime now);
}
