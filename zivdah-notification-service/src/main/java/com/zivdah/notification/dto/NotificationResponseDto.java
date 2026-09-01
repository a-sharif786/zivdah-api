package com.zivdah.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponseDto {

    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String recipientRole;
    private String notificationType;
    private String entityType;
    private Long entityId;
    private boolean isRead;
    private LocalDateTime readAt;
}
