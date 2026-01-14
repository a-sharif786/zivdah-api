package com.zivdah.notification.controller;

import com.zivdah.notification.dto.ApiResponse;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;

import com.zivdah.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/notifications")
@RequiredArgsConstructor
@CrossOrigin("*")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> sendNotification(
            @Valid @RequestBody NotificationRequestDto dto) {

        NotificationResponseDto response = notificationService.sendNotification(dto);

        return ResponseEntity.ok(ApiResponse.<NotificationResponseDto>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("Notification processed")
                .data(response)
                .build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getUserNotifications(
            @PathVariable Long userId) {

        List<NotificationResponseDto> notifications = notificationService.getNotificationsByUser(userId);

        return ResponseEntity.ok(ApiResponse.<List<NotificationResponseDto>>builder()
                .status("success")
                .statusCode(HttpStatus.OK.value())
                .message("User notifications fetched")
                .data(notifications)
                .build());
    }
}
