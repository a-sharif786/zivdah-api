package com.zivdah.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// One MPIN-enabled device of a user. deviceSecretHash is the hex SHA-256 of the secret issued at
// setup (the raw secret only ever exists in the setup response and the phone's Keystore/Keychain);
// mpinHash is BCrypt. revokedAt set = MPIN no longer usable on this device (lockout, revoke,
// password reset, deactivation, logout-all) until it is set up again.
@Table("mpin_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MpinDevice {

    @Id
    private Long id;
    private Long userId;
    private String deviceId;
    private String deviceName;
    private String deviceSecretHash;
    private String mpinHash;
    private int failedAttempts;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime revokedAt;
}
