package com.zivdah.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// One row per issued refresh token. tokenHash is the hex SHA-256 of the raw token — the raw
// value only ever exists in the login/refresh response. Every rotation of one login shares a
// familyId, so reuse of an already-rotated token can revoke the whole chain at once.
@Table("refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    private Long id;
    private Long userId;
    private String tokenHash;
    private String familyId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
