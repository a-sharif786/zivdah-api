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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public NotificationResponseDto sendNotification(NotificationRequestDto dto) {

        Notification notification = Notification.builder()
                .userId(dto.getUserId())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .status("PENDING")
                .build();

        Notification saved = notificationRepository.save(notification);

        if (dto.getFcmToken() != null && !dto.getFcmToken().isBlank()) {
            try {
                Message message = Message.builder()
                        .setToken(dto.getFcmToken())
                        .setNotification(
                                com.google.firebase.messaging.Notification.builder()
                                        .setTitle(dto.getTitle())
                                        .setBody(dto.getMessage())
                                        .build()
                        )
                        .putData("notificationId", saved.getId().toString())
                        .putData("status", "ORDER_SHIPPED")
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM response: {}", response);

                saved.setStatus("SENT");

            } catch (FirebaseMessagingException e) {
                log.error("FCM error", e);

                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    log.warn("FCM token is invalid or expired");
                    // TODO: remove token from DB
                }

                saved.setStatus("FAILED");

            } catch (Exception e) {
                log.error("Unexpected error sending FCM", e);
                saved.setStatus("FAILED");
            }
        }

        notificationRepository.save(saved);
        return mapToDto(saved);
    }

    @Override
    public List<NotificationResponseDto> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NotificationResponseDto mapToDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}
