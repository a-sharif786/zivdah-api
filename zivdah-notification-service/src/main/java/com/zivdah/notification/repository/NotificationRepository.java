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
    Flux<Notification> findAllBy(Pageable pageable);

    // Idempotency check — see NotificationServiceImpl#sendNotification.
    Mono<Notification> findByDedupKey(String dedupKey);

    // Backs the retry scheduler: FAILED sends that haven't exhausted their attempts and are
    // due (or overdue) for another try.
    Flux<Notification> findByStatusAndRetryCountLessThanAndNextRetryAtLessThanEqual(
            String status, int maxRetryCount, LocalDateTime now);
}
