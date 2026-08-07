package com.zivdah.notification.service;

import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationService {
    Mono<NotificationResponseDto> sendNotification(NotificationRequestDto dto);
    Flux<NotificationResponseDto> getNotificationsByUser(Long userId);
    Flux<NotificationResponseDto> getAllNotifications(Pageable pageable);
}
