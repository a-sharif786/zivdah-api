package com.zivdah.auth.repository;

import com.zivdah.auth.entity.RefreshToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

// Timestamps are passed in from the JVM (not NOW()) so revoked_at/expires_at/created_at all
// come from one clock and the purge cutoff compares like with like.
public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {

    Mono<RefreshToken> findByTokenHash(String tokenHash);

    // Conditional on revoked_at IS NULL so two concurrent refreshes with the same token can't
    // both succeed — exactly one gets 1 back, the other 0 (treated as reuse).
    @Modifying
    @Query("UPDATE refresh_tokens SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL")
    Mono<Integer> revokeIfActive(Long id, LocalDateTime now);

    @Modifying
    @Query("UPDATE refresh_tokens SET revoked_at = :now WHERE family_id = :familyId AND revoked_at IS NULL")
    Mono<Integer> revokeFamily(String familyId, LocalDateTime now);

    @Modifying
    @Query("UPDATE refresh_tokens SET revoked_at = :now WHERE user_id = :userId AND revoked_at IS NULL")
    Mono<Integer> revokeAllForUser(Long userId, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE expires_at < :cutoff OR revoked_at < :cutoff")
    Mono<Integer> deleteExpiredOrRevokedBefore(LocalDateTime cutoff);
}
