package com.zivdah.auth.security;

import com.zivdah.auth.entity.RefreshToken;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.exception.InvalidRefreshTokenException;
import com.zivdah.auth.repository.RefreshTokenRepository;
import com.zivdah.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

// Refresh tokens are deliberately opaque random strings, NOT JWTs: every service in the
// platform validates any HS256 JWT signed with the shared jwt.secret, so a JWT refresh token
// would double as a (long-lived) access token everywhere. Only the SHA-256 hash is persisted —
// the raw token has 256 bits of entropy, so a fast hash is sufficient (and keeps lookup a
// single indexed equality match, which BCrypt's per-row salt would rule out).
//
// Rotation: each successful refresh revokes the presented token and issues a new one in the
// same family. Presenting an already-revoked token means it was copied/stolen (or replayed),
// so the whole family is revoked and that login must sign in again.
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token-expiration:7d}")
    private Duration refreshTokenExpiration;

    // The user a refresh token belongs to (re-read from the DB, so role/active changes take
    // effect on the next refresh) plus the newly issued replacement token.
    public record Rotation(UserEntity user, String refreshToken) {}

    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpiration.toSeconds();
    }

    // familyId null = a fresh login, starting a new rotation chain.
    public Mono<String> issue(Long userId, String familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .familyId(familyId != null ? familyId : UUID.randomUUID().toString())
                .createdAt(now)
                .expiresAt(now.plus(refreshTokenExpiration))
                .build();
        return refreshTokenRepository.save(entity).thenReturn(rawToken);
    }

    public Mono<Rotation> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Mono.error(new InvalidRefreshTokenException("Refresh token is required"));
        }
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .switchIfEmpty(Mono.error(new InvalidRefreshTokenException("Invalid refresh token")))
                .flatMap(token -> {
                    if (token.getRevokedAt() != null) {
                        log.warn("Revoked refresh token reused — revoking its token family (userId={})", token.getUserId());
                        return revokeFamilyAndFail(token, "Refresh token has been revoked");
                    }
                    if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new InvalidRefreshTokenException("Refresh token has expired"));
                    }
                    return refreshTokenRepository.revokeIfActive(token.getId(), LocalDateTime.now())
                            .flatMap(updated -> updated == 0
                                    // Lost a race with a concurrent refresh of the same token.
                                    ? revokeFamilyAndFail(token, "Refresh token has been revoked")
                                    : userRepository.findById(token.getUserId())
                                            .filter(UserEntity::isActive)
                                            .switchIfEmpty(Mono.defer(() ->
                                                    revokeFamilyAndFail(token, "Account is deactivated")))
                                            .flatMap(user -> issue(user.getId(), token.getFamilyId())
                                                    .map(newToken -> new Rotation(user, newToken))));
                });
    }

    // Revokes one token (logout from a single device). Unknown/already-revoked is a no-op.
    public Mono<Void> revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Mono.empty();
        }
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .flatMap(token -> refreshTokenRepository.revokeIfActive(token.getId(), LocalDateTime.now()))
                .then();
    }

    // Signs the user out everywhere (logout without a token, deactivation, password reset).
    public Mono<Void> revokeAll(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now()).then();
    }

    // Keeps the table from growing forever. Rows are kept for a day after expiry/revocation so
    // a replayed token still hits the reuse-detection path above rather than "not found".
    @Scheduled(cron = "${jwt.refresh-token-purge-cron:0 0 3 * * *}")
    public void purgeStaleTokens() {
        refreshTokenRepository.deleteExpiredOrRevokedBefore(LocalDateTime.now().minusDays(1))
                .subscribe(
                        count -> log.info("Purged {} stale refresh tokens", count),
                        e -> log.error("Failed to purge stale refresh tokens", e));
    }

    private <T> Mono<T> revokeFamilyAndFail(RefreshToken token, String message) {
        return refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now())
                .then(Mono.error(new InvalidRefreshTokenException(message)));
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
