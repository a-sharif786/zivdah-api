package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.MessageStatus;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import com.zivdah.chat.kafka.ChatKafkaProducer;
import com.zivdah.chat.dto.ws.MessagePayload;
import com.zivdah.chat.dto.ws.WsEnvelope;
import com.zivdah.chat.repository.ConversationRepository;
import com.zivdah.chat.repository.MessageRepository;
import com.zivdah.chat.service.MessageService;
import com.zivdah.chat.websocket.SessionRegistry;
import com.zivdah.common.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int MESSAGE_PREVIEW_LENGTH = 100;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final DatabaseClient databaseClient;
    private final ChatKafkaProducer chatKafkaProducer;
    private final SessionRegistry sessionRegistry;

    @Override
    public Mono<ChatMessage> persistMessage(Long conversationId, Long senderId, SenderType senderType,
                                             MessageType messageType, String message, String attachmentUrl) {
        return messageRepository.save(ChatMessage.builder()
                        .conversationId(conversationId)
                        .senderId(senderId)
                        .senderType(senderType)
                        .messageType(messageType != null ? messageType : MessageType.TEXT)
                        .message(message)
                        .attachmentUrl(attachmentUrl)
                        .status(MessageStatus.SENT)
                        .createdAt(LocalDateTime.now())
                        .build())
                .flatMap(saved -> publishMessageSentEvent(saved).thenReturn(saved));
    }

    // Publishes ChatMessageSentEvent for CUSTOMER/AGENT messages only — BOT and SYSTEM messages
    // are server-authored and don't need a "someone sent you a message" notification. Looks up the
    // conversation to find the *other* party to notify (assignedAgentId for a CUSTOMER message,
    // customerId for an AGENT message); if that party doesn't exist yet (no agent assigned yet),
    // skips publishing entirely — there's no one to notify.
    private Mono<Void> publishMessageSentEvent(ChatMessage saved) {
        if (saved.getSenderType() != SenderType.CUSTOMER && saved.getSenderType() != SenderType.AGENT) {
            return Mono.empty();
        }
        return conversationRepository.findById(saved.getConversationId())
                .flatMap(conversation -> {
                    Long recipientUserId = resolveRecipient(conversation, saved.getSenderType());
                    if (recipientUserId == null) {
                        return Mono.<Void>empty();
                    }
                    return Mono.fromRunnable(() -> chatKafkaProducer.publishMessageSent(ChatMessageSentEvent.builder()
                            .conversationId(saved.getConversationId())
                            .messageId(saved.getId())
                            .senderId(saved.getSenderId())
                            .senderType(saved.getSenderType().name())
                            .recipientUserId(recipientUserId)
                            .messagePreview(truncate(saved.getMessage()))
                            .build()));
                })
                .then();
    }

    private Long resolveRecipient(ChatConversation conversation, SenderType senderType) {
        return senderType == SenderType.CUSTOMER ? conversation.getAssignedAgentId() : conversation.getCustomerId();
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MESSAGE_PREVIEW_LENGTH) {
            return message;
        }
        return message.substring(0, MESSAGE_PREVIEW_LENGTH);
    }

    @Override
    public Mono<ChatMessage> persistSystemMessage(Long conversationId, String message) {
        return persistMessage(conversationId, null, SenderType.SYSTEM, MessageType.SYSTEM, message, null);
    }

    @Override
    public Mono<ChatMessage> sendMessage(Long conversationId, Long senderId, SenderType senderType,
                                          MessageType messageType, String message, String attachmentUrl) {
        return persistMessage(conversationId, senderId, senderType, messageType, message, attachmentUrl)
                .doOnNext(saved -> sessionRegistry.broadcast(conversationId, toEnvelope(saved)));
    }

    // Mirrors ChatWebSocketHandler#toMessageEnvelope's envelope shape exactly — kept as its own
    // small copy here rather than refactoring the (already-tested) WS handler to call this method
    // too, to avoid touching that live code path for this addition.
    private WsEnvelope toEnvelope(ChatMessage m) {
        String frameType = m.getSenderType() == SenderType.SYSTEM ? "SYSTEM" : "MESSAGE";
        return WsEnvelope.builder()
                .type(frameType)
                .payload(MessagePayload.builder()
                        .id(m.getId())
                        .conversationId(m.getConversationId())
                        .senderId(m.getSenderId())
                        .senderType(m.getSenderType().name())
                        .messageType(m.getMessageType().name())
                        .message(m.getMessage())
                        .attachmentUrl(m.getAttachmentUrl())
                        .status(m.getStatus().name())
                        .createdAt(m.getCreatedAt())
                        .build())
                .build();
    }

    // Hand-written against DatabaseClient rather than a derived/@Query repository method — same
    // reasoning as PaymentStatsRepository/InvoiceNumberGenerator elsewhere in the platform: this
    // is a bulk conditional UPDATE ("every message from the other party up to this id"), not a
    // single-entity save a ReactiveCrudRepository derived query can express.
    @Override
    public Mono<Long> markOtherPartyMessagesRead(Long conversationId, Long lastReadMessageId,
                                                  SenderType callerSenderType) {
        return databaseClient.sql(
                        "UPDATE messages SET status = 'READ' " +
                        "WHERE conversation_id = :conversationId AND id <= :lastReadMessageId " +
                        "AND sender_type <> :callerSenderType")
                .bind("conversationId", conversationId)
                .bind("lastReadMessageId", lastReadMessageId)
                .bind("callerSenderType", callerSenderType.name())
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> log.debug("Marked {} messages READ in conversation {} (caller senderType={})",
                        rows, conversationId, callerSenderType));
    }
}
