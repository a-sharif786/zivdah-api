package com.zivdah.notification.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class NotificationRequestDto {
    private Long userId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private String fcmToken; // optional — resolved from device_tokens via userId when absent

    // Metadata for the notification-history screens — all optional, but callers should set
    // them so the history API is actually useful (see NotificationController).
    private String recipientRole;
    private String notificationType;
    private String entityType;
    private Long entityId;

    // Idempotency key (e.g. "orderId:notificationType:userId"): if a notification with this
    // key already exists, sendNotification() returns it as-is instead of sending again — an
    // at-least-once Kafka redelivery shouldn't double-push. Optional; omit for calls that
    // don't need dedup (e.g. the ad-hoc REST /send endpoint).
    private String dedupKey;
}
