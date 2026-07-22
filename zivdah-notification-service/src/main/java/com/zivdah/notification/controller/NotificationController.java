package com.zivdah.notification.controller;

import com.zivdah.notification.dto.ApiResponse;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/notifications")
@RequiredArgsConstructor
@CrossOrigin("*")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public Mono<ResponseEntity<ApiResponse<NotificationResponseDto>>> sendNotification(
            @RequestBody NotificationRequestDto dto) {
        return notificationService.sendNotification(dto)
                .map(r -> ResponseEntity.ok(ApiResponse.<NotificationResponseDto>builder()
                        .status("success").statusCode(200).message("Notification processed").data(r).build()));
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<ApiResponse<List<NotificationResponseDto>>>> getUserNotifications(
            @PathVariable Long userId) {
        return notificationService.getNotificationsByUser(userId)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<NotificationResponseDto>>builder()
                        .status("success").statusCode(200).message("User notifications fetched").data(list).build()));
    }
}
