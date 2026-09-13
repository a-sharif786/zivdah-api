package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponseDto {

    private Long id;
    private Long customerId;

    // ConversationType name (BOT/HUMAN)
    private String type;

    // ConversationStatus name (OPEN/WAITING/ACTIVE/CLOSED)
    private String status;

    private Long assignedAgentId;
    private Long orderId;
    private String topic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
