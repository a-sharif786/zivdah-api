package com.zivdah.notification.scheduler;

import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// Extension point for a future Kafka DLQ/retry-topic: this in-process scheduler is enough
// at this app's scale (no separate infra), but callers only ever depend on
// NotificationService, so swapping the mechanism later doesn't touch anything upstream.
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryFailedNotifications() {
        notificationService.retryFailedNotifications()
                .doOnError(e -> log.error("Notification retry pass failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .block();
    }
}
