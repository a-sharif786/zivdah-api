package com.zivdah.chat.entity;

import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.enums.ConversationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {

    @Id
    private Long id;

    private Long customerId;

    private ConversationType type;

    private ConversationStatus status;

    private Long assignedAgentId;

    private Long orderId;

    // ORDER_STATUS/REFUND/PRODUCT/DELIVERY/ACCOUNT/GENERAL, set by intent classifier, feeds analytics
    private String topic;

    // BOT -> HUMAN transition time; feeds "first response time" metric
    private LocalDateTime handedOffAt;

    // Set once WaitingConversationEscalationScheduler has nagged admins about this conversation
    // still being unclaimed — null means "not escalated yet" (escalate once, not repeatedly).
    private LocalDateTime escalatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
