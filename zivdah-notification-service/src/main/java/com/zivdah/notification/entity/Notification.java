package com.zivdah.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String status; // SENT, FAILED, PENDING
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
