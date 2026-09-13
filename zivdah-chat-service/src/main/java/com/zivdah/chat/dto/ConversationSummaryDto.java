package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSummaryDto {
    private Long id;
    private Long customerId;
    private String type;
    private String status;
    private Long assignedAgentId;
    private Long orderId;
    private String topic;
    private String lastMessagePreview;
    // Not computed in this pass (would need per-recipient read-state accounting on top of the
    // per-message status column) — always 0 for now; the frontend already derives its own unread
    // indicator client-side from individual messages' status field.
    private int unreadCount;
    // Present once a CLOSED conversation has been rated, else null — lets list/history pages show
    // it without an extra per-row fetch.
    private Short rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
