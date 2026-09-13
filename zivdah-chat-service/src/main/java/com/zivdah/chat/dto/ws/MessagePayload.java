package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

// Carries a chat message both directions over the WS envelope:
//  - inbound (client -> server, type=MESSAGE): only conversationId, message, messageType and
//    clientMessageId are read; id/senderId/senderType/status/createdAt are ignored if a client
//    sends them (never trusted from the wire).
//  - outbound (server -> client, MESSAGE/SYSTEM frames): every field is populated from the
//    persisted ChatMessage row, echoing clientMessageId back so the sender can reconcile its
//    optimistic bubble with the real DB id.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessagePayload {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderType;
    private String messageType;
    private String message;
    private String attachmentUrl;
    private String status;
    private String clientMessageId;
    private LocalDateTime createdAt;
    // Only set on the SYSTEM frame that accompanies a close (see ConversationServiceImpl#
    // closeConversation) — the customer-facing widget (zivdah-web ChatContext) watches
    // specifically for conversationStatus === "CLOSED" on SYSTEM frames to leave HUMAN mode and
    // return to the bot. Null on every other frame (regular messages, "agent joined", etc.).
    private String conversationStatus;
}
