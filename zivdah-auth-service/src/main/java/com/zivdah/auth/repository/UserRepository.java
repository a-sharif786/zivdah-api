package com.zivdah.auth.repository;

import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.enums.Role;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface UserRepository extends ReactiveCrudRepository<UserEntity, Long> {
    Mono<UserEntity> findByEmail(String email);
    Mono<Boolean> existsByEmail(String email);
    Mono<Boolean> existsByMobile(String mobile);
    Mono<UserEntity> findByMobile(String mobile);
    Mono<Long> countByRole(Role role);
    Flux<UserEntity> findByRole(Role role);
    // UserEntity has no createdAt, so id DESC is the closest available proxy for "newest first"
    // in the admin Users list.
    Flux<UserEntity> findAllByOrderByIdDesc();

    // --- mobile OTP (MobileOtpService) — all atomic, so concurrent requests can't race them ---

    // Resend cooldown: 1 = this request may send an SMS, 0 = one was sent within the cooldown.
    @Modifying
    @Query("UPDATE users SET otp_generated_at = :now WHERE id = :id " +
            "AND (otp_generated_at IS NULL OR otp_generated_at < :cutoff)")
    Mono<Integer> claimOtpSend(Long id, LocalDateTime now, LocalDateTime cutoff);

    // Provider call failed — give the cooldown back so the user can retry straight away.
    @Modifying
    @Query("UPDATE users SET otp_generated_at = NULL WHERE id = :id")
    Mono<Integer> releaseOtpSend(Long id);

    @Modifying
    @Query("UPDATE users SET mobile_otp_ref = :ref, mobile_otp_attempts = 0, mobile_otp = NULL WHERE id = :id")
    Mono<Integer> storeOtpRef(Long id, String ref);

    // Reserves one verify attempt BEFORE the provider is asked; 0 = no attempts left (or the
    // OTP was replaced/consumed meanwhile).
    @Modifying
    @Query("UPDATE users SET mobile_otp_attempts = mobile_otp_attempts + 1 " +
            "WHERE id = :id AND mobile_otp_ref = :ref AND mobile_otp_attempts < :max")
    Mono<Integer> reserveOtpAttempt(Long id, String ref, int max);

    @Modifying
    @Query("UPDATE users SET mobile_otp_ref = NULL, mobile_otp_attempts = 0 WHERE id = :id AND mobile_otp_ref = :ref")
    Mono<Integer> clearOtpRef(Long id, String ref);
}
