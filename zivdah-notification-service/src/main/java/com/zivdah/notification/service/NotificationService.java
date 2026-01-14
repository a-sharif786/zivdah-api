package com.zivdah.notification.service;

import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;

import java.util.List;

public interface NotificationService {
    NotificationResponseDto sendNotification(NotificationRequestDto dto);

    List<NotificationResponseDto> getNotificationsByUser(Long userId);
}
