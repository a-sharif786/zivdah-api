package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponseDto {

    private Long id;
    private Long conversationId;
    private Long senderId;

    // SenderType name (CUSTOMER/BOT/AGENT/SYSTEM)
    private String senderType;

    // MessageType name (TEXT/IMAGE/FILE/SYSTEM)
    private String messageType;

    private String message;
    private String attachmentUrl;

    // MessageStatus name (SENT/DELIVERED/READ)
    private String status;

    private LocalDateTime createdAt;
}
