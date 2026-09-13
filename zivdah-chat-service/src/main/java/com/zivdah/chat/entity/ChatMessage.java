package com.zivdah.chat.entity;

import com.zivdah.chat.enums.MessageStatus;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    private Long id;

    private Long conversationId;

    // Null for BOT/SYSTEM messages — only CUSTOMER/AGENT messages carry a real user id.
    private Long senderId;

    private SenderType senderType;

    private MessageType messageType;

    private String message;

    private String attachmentUrl;

    private MessageStatus status;

    private LocalDateTime createdAt;
}
