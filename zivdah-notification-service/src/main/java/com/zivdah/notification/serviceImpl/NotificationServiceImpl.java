package com.zivdah.notification.serviceImpl;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.zivdah.notification.client.AuthServiceClient;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import com.zivdah.notification.entity.Notification;
import com.zivdah.notification.repository.NotificationRepository;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_BACKOFF_MINUTES = 2;

    private final NotificationRepository notificationRepository;
    private final AuthServiceClient authServiceClient;

    @Override
    public Mono<NotificationResponseDto> sendNotification(NotificationRequestDto dto) {
        String dedupKey = dto.getDedupKey();
        Mono<Notification> existing = (dedupKey != null && !dedupKey.isBlank())
                ? notificationRepository.findByDedupKey(dedupKey)
                : Mono.empty();

        // An at-least-once Kafka redelivery (or a retried producer call) with the same
        // dedupKey returns the notification already on file instead of sending again.
        return existing
                .switchIfEmpty(Mono.defer(() -> createAndSend(dto)))
                .map(this::mapToDto);
    }

    private Mono<Notification> createAndSend(NotificationRequestDto dto) {
        LocalDateTime now = LocalDateTime.now();
        Notification notification = Notification.builder()
                .userId(dto.getUserId())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .status("PENDING")
                .recipientRole(dto.getRecipientRole())
                .notificationType(dto.getNotificationType())
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .dedupKey(dto.getDedupKey())
                .isRead(false)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Callers that only have a userId — every Kafka-triggered notification — never set
        // fcmToken; resolve every active device for that user instead (they may be signed
        // in on several devices/browsers at once).
        Mono<List<String>> tokensMono = (dto.getFcmToken() != null && !dto.getFcmToken().isBlank())
                ? Mono.just(List.of(dto.getFcmToken()))
                : (dto.getUserId() != null ? authServiceClient.getActiveDeviceTokens(dto.getUserId()) : Mono.just(List.of()));

        return notificationRepository.save(notification)
                .flatMap(saved -> tokensMono.flatMap(tokens -> deliver(saved, tokens, INITIAL_BACKOFF_MINUTES)))
                .flatMap(notificationRepository::save);
    }

    /** Sends to every token via one multicast call, deactivates any token Firebase reports
     *  as unregistered, and sets status/nextRetryAt on {@code saved} — SENT if at least one
     *  token succeeded, else FAILED with a retry scheduled {@code backoffMinutes} out. Never
     *  errors: an empty token list or an FCM-level failure both just leave the notification
     *  PENDING/FAILED rather than propagating. */
    private Mono<Notification> deliver(Notification saved, List<String> tokens, int backoffMinutes) {
        if (tokens.isEmpty()) {
            return Mono.just(saved); // nothing registered for this user — stays PENDING
        }
        // Firebase Admin SDK is blocking — offload to boundedElastic
        return Mono.fromCallable(() -> sendMulticast(saved, tokens))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    if (!result.invalidTokens().isEmpty()) {
                        // Fire-and-forget — a failure to deactivate just means that dead
                        // token gets tried (and fails) again next time, not worth retrying.
                        Flux.fromIterable(result.invalidTokens())
                                .flatMap(authServiceClient::deactivateToken)
                                .subscribe();
                    }
                    if (result.successCount() > 0) {
                        saved.setStatus("SENT");
                        saved.setNextRetryAt(null);
                    } else {
                        saved.setStatus("FAILED");
                        saved.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                    }
                    return saved;
                })
                .onErrorResume(e -> {
                    log.error("FCM error sending notification {}: {}", saved.getId(), e.getMessage());
                    saved.setStatus("FAILED");
                    saved.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                    return Mono.just(saved);
                });
    }

    private SendResult sendMulticast(Notification saved, List<String> tokens) throws Exception {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(saved.getTitle())
                        .setBody(saved.getMessage())
                        .build())
                .putData("notificationId", String.valueOf(saved.getId()))
                .build();

        BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
        List<SendResponse> responses = response.getResponses();
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (!r.isSuccessful() && r.getException() != null
                    && r.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                invalidTokens.add(tokens.get(i));
            }
        }
        return new SendResult(response.getSuccessCount(), invalidTokens);
    }

    private record SendResult(int successCount, List<String> invalidTokens) {}

    @Override
    public Flux<NotificationResponseDto> sendToMany(List<Long> userIds, String title, String message,
                                                      String recipientRole, String notificationType,
                                                      String entityType, Long entityId, String dedupKeyPrefix) {
        return Flux.fromIterable(userIds)
                .flatMap(userId -> {
                    NotificationRequestDto dto = new NotificationRequestDto();
                    dto.setUserId(userId);
                    dto.setTitle(title);
                    dto.setMessage(message);
                    dto.setRecipientRole(recipientRole);
                    dto.setNotificationType(notificationType);
                    dto.setEntityType(entityType);
                    dto.setEntityId(entityId);
                    if (dedupKeyPrefix != null && !dedupKeyPrefix.isBlank()) {
                        dto.setDedupKey(dedupKeyPrefix + ":" + userId);
                    }
                    return sendNotification(dto);
                });
    }

    @Override
    public Flux<NotificationResponseDto> getNotificationsByUser(Long userId, boolean unreadOnly) {
        return (unreadOnly ? notificationRepository.findByUserIdAndIsReadFalse(userId)
                : notificationRepository.findByUserId(userId))
                .map(this::mapToDto);
    }

    @Override
    public Flux<NotificationResponseDto> getAllNotifications(Pageable pageable) {
        return notificationRepository.findAllBy(pageable).map(this::mapToDto);
    }

    @Override
    public Mono<NotificationResponseDto> markAsRead(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")))
                .flatMap(n -> {
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    return notificationRepository.save(n);
                })
                .map(this::mapToDto);
    }

    @Override
    public Mono<Void> retryFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();
        return notificationRepository
                .findByStatusAndRetryCountLessThanAndNextRetryAtLessThanEqual("FAILED", MAX_RETRIES, now)
                .flatMap(n -> {
                    n.setRetryCount(n.getRetryCount() + 1);
                    int backoff = INITIAL_BACKOFF_MINUTES * (n.getRetryCount() + 1); // widening backoff per attempt
                    Mono<List<String>> tokensMono = n.getUserId() != null
                            ? authServiceClient.getActiveDeviceTokens(n.getUserId())
                            : Mono.just(List.of());
                    return tokensMono
                            .flatMap(tokens -> deliver(n, tokens, backoff))
                            .flatMap(notificationRepository::save);
                })
                .then();
    }

    private NotificationResponseDto mapToDto(Notification n) {
        return NotificationResponseDto.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .message(n.getMessage())
                .status(n.getStatus())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .recipientRole(n.getRecipientRole())
                .notificationType(n.getNotificationType())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .build();
    }
}
