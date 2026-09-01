package com.zivdah.notification.service;

import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NotificationService {
    Mono<NotificationResponseDto> sendNotification(NotificationRequestDto dto);

    Flux<NotificationResponseDto> getNotificationsByUser(Long userId, boolean unreadOnly);

    Flux<NotificationResponseDto> getAllNotifications(Pageable pageable);

    Mono<NotificationResponseDto> markAsRead(Long notificationId);

    /** Sends the same title/message to each of several recipients — every order-lifecycle
     *  event with more than one recipient role (customer+vendor+admin, etc.) uses this
     *  instead of repeating the same fan-out loop at each call site. dedupKeyPrefix, when
     *  set, becomes "{prefix}:{userId}" per recipient so a redelivered Kafka event doesn't
     *  push the same notification twice to the same person. */
    Flux<NotificationResponseDto> sendToMany(List<Long> userIds, String title, String message,
                                              String recipientRole, String notificationType,
                                              String entityType, Long entityId, String dedupKeyPrefix);

    /** Re-attempts FAILED sends that haven't exhausted their retry budget. Called by
     *  NotificationRetryScheduler; safe to call any time (a no-op when nothing is due). */
    Mono<Void> retryFailedNotifications();
}
