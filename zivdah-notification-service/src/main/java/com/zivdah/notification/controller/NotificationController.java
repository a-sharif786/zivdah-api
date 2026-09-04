package com.zivdah.notification.controller;

import com.zivdah.notification.dto.ApiResponse;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.dto.NotificationResponseDto;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @PathVariable Long userId, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.getNotificationsByUser(userId, unreadOnly)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<NotificationResponseDto>>builder()
                        .status("success").statusCode(200).message("User notifications fetched").data(list).build()));
    }

    @PatchMapping("/{notificationId}/read")
    public Mono<ResponseEntity<ApiResponse<NotificationResponseDto>>> markAsRead(@PathVariable Long notificationId) {
        return notificationService.markAsRead(notificationId)
                .map(r -> ResponseEntity.ok(ApiResponse.<NotificationResponseDto>builder()
                        .status("success").statusCode(200).message("Notification marked read").data(r).build()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<NotificationResponseDto>>>> getAllNotifications(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return notificationService.getAllNotifications(PageRequest.of(page, size))
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<NotificationResponseDto>>builder()
                        .status("success").statusCode(200).message("Notifications retrieved").data(list).build()));
    }
}
