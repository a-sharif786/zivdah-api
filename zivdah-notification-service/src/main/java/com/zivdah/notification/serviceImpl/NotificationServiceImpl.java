package com.zivdah.notification.serviceImpl;


import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
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

        // Send via Firebase if FCM token provided
        if (dto.getFcmToken() != null && !dto.getFcmToken().isBlank()) {
            try {
                Message message = Message.builder()
                        .setToken(dto.getFcmToken())
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(dto.getTitle())
                                .setBody(dto.getMessage())
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Notification sent via FCM: " + response);
                saved.setStatus("SENT");
            } catch (Exception e) {
                log.error("Error sending FCM notification", e);
                saved.setStatus("FAILED");
            }
        } else {
            saved.setStatus("PENDING");
        }

        saved = notificationRepository.save(saved);
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
