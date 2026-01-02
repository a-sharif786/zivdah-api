package com.zivdah.notification.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "push_notification")
public class PushNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;

    @Column(name = "notification_type")
    private String notificationType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "trigger_event", columnDefinition = "text")
    private String triggerEvent;

    private String timing;

    private String priority;

    @Column(name = "user_segment")
    private String userSegment;

    private String channel;

    @Column(name = "frequency_cap")
    private String frequencyCap;

    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt =LocalDateTime.now() ;
}
