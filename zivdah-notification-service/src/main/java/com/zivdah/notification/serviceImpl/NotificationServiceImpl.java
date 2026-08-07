package com.zivdah.notification.serviceImpl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import com.zivdah.notification.entity.Notification;
import com.zivdah.notification.repository.NotificationRepository;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public Mono<NotificationResponseDto> sendNotification(NotificationRequestDto dto) {
        Notification notification = Notification.builder()
                .userId(dto.getUserId())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return notificationRepository.save(notification)
                .flatMap(saved -> {
                    if (dto.getFcmToken() == null || dto.getFcmToken().isBlank()) {
                        return Mono.just(saved);
                    }
                    Message fcmMessage = Message.builder()
                            .setToken(dto.getFcmToken())
                            .setNotification(com.google.firebase.messaging.Notification.builder()
                                    .setTitle(dto.getTitle())
                                    .setBody(dto.getMessage())
                                    .build())
                            .putData("notificationId", saved.getId().toString())
                            .build();

                    // Firebase Admin SDK is blocking — offload to boundedElastic
                    return Mono.fromCallable(() -> {
                                FirebaseMessaging.getInstance().send(fcmMessage);
                                saved.setStatus("SENT");
                                return saved;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("FCM error: {}", e.getMessage());
                                if (e instanceof FirebaseMessagingException fme
                                        && fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                                    log.warn("FCM token invalid/expired");
                                }
                                saved.setStatus("FAILED");
                                return Mono.just(saved);
                            });
                })
                .flatMap(notificationRepository::save)
                .map(this::mapToDto);
    }

    @Override
    public Flux<NotificationResponseDto> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserId(userId).map(this::mapToDto);
    }

    @Override
    public Flux<NotificationResponseDto> getAllNotifications(Pageable pageable) {
        return notificationRepository.findAllBy(pageable).map(this::mapToDto);
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
                .build();
    }
}
