package com.zivdah.chat.service;

import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import reactor.core.publisher.Mono;

public interface MessageService {

    // Single shared persistence path for every chat message, regardless of where it comes from —
    // the bot HTTP flow (ChatbotServiceImpl), the live WebSocket handler (ChatWebSocketHandler),
    // and a later REST-fallback send/attachment endpoint all call exactly this, so
    // senderType/status/createdAt handling never drifts between code paths. senderId is null for
    // BOT/SYSTEM messages (see ChatMessage's own comment).
    Mono<ChatMessage> persistMessage(Long conversationId, Long senderId, SenderType senderType,
                                      MessageType messageType, String message, String attachmentUrl);

    // Convenience for server-authored notices ("Agent has joined the chat.", "Conversation
    // closed.") — senderId=null, messageType=SYSTEM, so they persist and replay identically to
    // any other message (see ChatWebSocketHandler's reconnect-replay).
    Mono<ChatMessage> persistSystemMessage(Long conversationId, String message);

    // Bulk-marks the OTHER party's messages (sender_type <> callerSenderType) up to
    // lastReadMessageId as READ in one statement — backs the READ_RECEIPT websocket frame.
    // Returns the number of rows updated.
    Mono<Long> markOtherPartyMessagesRead(Long conversationId, Long lastReadMessageId, SenderType callerSenderType);

    // persistMessage() + a live broadcast to every session on this conversation — for the REST
    // fallback send endpoint and the attachment-upload endpoint, neither of which goes through
    // ChatWebSocketHandler's own persist-then-broadcast code (that path stays untouched; this is
    // a second, REST-side caller of the same broadcast mechanism via SessionRegistry).
    Mono<ChatMessage> sendMessage(Long conversationId, Long senderId, SenderType senderType,
                                   MessageType messageType, String message, String attachmentUrl);
}
