package com.zivdah.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String status; // SENT, FAILED, PENDING
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Recipient audience this notification was addressed to (ADMIN/VENDOR/USER/DELIVERY_BOY)
    // — not necessarily the recipient's *current* role, just what they were addressed as.
    private String recipientRole;
    // ORDER_CREATED, DELIVERY_ASSIGNED, ORDER_STATUS_CHANGED, DELIVERY_FAILED,
    // DELIVERY_COMPLETED, PAYMENT_COMPLETED, ...
    private String notificationType;
    private String entityType; // e.g. "ORDER", "DELIVERY"
    private Long entityId;

    private boolean isRead;
    private LocalDateTime readAt;

    // Producer-supplied idempotency key (e.g. "orderId:notificationType:userId") — null for
    // callers that don't need dedup. See NotificationServiceImpl#sendNotification.
    private String dedupKey;
    private int retryCount;
    private LocalDateTime nextRetryAt;
}
