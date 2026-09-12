package com.zivdah.notification.repository;

import com.zivdah.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    Flux<Notification> findByUserId(Long userId);
    Flux<Notification> findByUserIdAndIsReadFalse(Long userId);
    // OrderByCreatedAtDesc: admin notification list should show new notifications on top
    // (matches the sort NotificationsPage's vendor/customer siblings already apply client-side).
    Flux<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Idempotency check — see NotificationServiceImpl#sendNotification.
    Mono<Notification> findByDedupKey(String dedupKey);

    // Backs the retry scheduler: FAILED sends that haven't exhausted their attempts and are
    // due (or overdue) for another try.
    Flux<Notification> findByStatusAndRetryCountLessThanAndNextRetryAtLessThanEqual(
            String status, int maxRetryCount, LocalDateTime now);
}
