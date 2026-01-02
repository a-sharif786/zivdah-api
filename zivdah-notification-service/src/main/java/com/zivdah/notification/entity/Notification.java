package com.zivdah.notification.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "device_token", nullable = false)
    private String deviceToken;
}
