package com.zivdah.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("conversation_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationRating {

    @Id
    private Long id;

    private Long conversationId;

    private Long customerId;

    private Long agentId;

    private Short rating;

    private String feedback;

    private LocalDateTime createdAt;
}
