package com.zivdah.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// One row per physical device/browser registration — a user may have several active rows
// at once (phone + laptop + a second browser). See device_tokens migration for the schema.
@Table("device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    private Long id;
    private Long userId;
    private String userRole;
    private String deviceType; // ANDROID, IOS, WEB
    private String fcmToken;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
