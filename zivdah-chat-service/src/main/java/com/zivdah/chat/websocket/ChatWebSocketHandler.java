package com.zivdah.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.chat.dto.ws.ErrorPayload;
import com.zivdah.chat.dto.ws.MessagePayload;
import com.zivdah.chat.dto.ws.PresencePayload;
import com.zivdah.chat.dto.ws.ReadReceiptPayload;
import com.zivdah.chat.dto.ws.TypingPayload;
import com.zivdah.chat.dto.ws.WsEnvelope;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import com.zivdah.chat.repository.ConversationRepository;
import com.zivdah.chat.repository.MessageRepository;
import com.zivdah.chat.security.JwtTokenProvider;
import com.zivdah.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;

// Native reactive WebSocketHandler for /ws/chat/{conversationId} — NOT STOMP/SockJS (this
// platform is all-WebFlux, with no servlet-based STOMP broker available). One socket per
// conversation per side; registered/broadcast via SessionRegistry.
//
// Auth: the JWT arrives as a `token` query param (a WS handshake can't carry an Authorization
// header) and is validated as the very first thing in handle(), before any registry mutation or
// DB access — invalid/missing closes immediately with POLICY_VIOLATION. SecurityConfig permits
// this path at the filter-chain level; this handler is the real gate.
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    private static final String PATH_MARKER = "/ws/chat/";
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final JwtTokenProvider jwtTokenProvider;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        Long conversationId = extractConversationId(uri);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String token = query.getFirst("token");

        if (conversationId == null || token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            log.warn("Rejecting WS handshake: missing/invalid token or conversation id (uri={})", uri);
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        Long userId;
        String role;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(token);
            role = jwtTokenProvider.getRoleFromToken(token);
        } catch (Exception ex) {
            log.warn("Rejecting WS handshake: could not read claims from token", ex);
            return session.close(CloseStatus.POLICY_VIOLATION);
        }
        if (userId == null || role == null) {
            log.warn("Rejecting WS handshake: token has no userId/role claim");
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        String normalizedRole = role.toUpperCase();
        long lastMessageId = parseLastMessageId(query.getFirst("lastMessageId"));
        Long finalConversationId = conversationId;
        Long finalUserId = userId;

        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new SecurityException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    boolean readOnly = resolveReadOnly(conversation, finalUserId, normalizedRole);
                    return runSession(session, finalConversationId, finalUserId, normalizedRole, readOnly, lastMessageId,
                            conversation.getStatus());
                })
                .onErrorResume(SecurityException.class, ex -> {
                    log.warn("Rejecting WS handshake for conversation {} (userId={}, role={}): {}",
                            finalConversationId, finalUserId, normalizedRole, ex.getMessage());
                    return session.close(CloseStatus.POLICY_VIOLATION);
                });
    }

    // USER: only its own conversation. ADMIN: only if assigned, OR a read-only preview while the
    // conversation is still WAITING (unclaimed) — anything else throws, closing the socket with
    // POLICY_VIOLATION up in handle(). Either way, a CLOSED conversation is always read-only —
    // otherwise a customer (or agent) whose session outlives the close keeps sending messages
    // into a room nobody's listening to anymore (confirmed 2026-09-13: messages 19-21 on
    // conversation 2 were persisted and broadcast several minutes after its "Conversation
    // closed." system message, into an empty room).
    private boolean resolveReadOnly(ChatConversation conversation, Long userId, String role) {
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            return true;
        }
        if ("USER".equals(role)) {
            if (conversation.getCustomerId() != null && conversation.getCustomerId().equals(userId)) {
                return false;
            }
            throw new SecurityException("Customer does not own conversation " + conversation.getId());
        }
        if ("ADMIN".equals(role)) {
            if (conversation.getAssignedAgentId() != null && conversation.getAssignedAgentId().equals(userId)) {
                return false;
            }
            if (conversation.getStatus() == ConversationStatus.WAITING) {
                return true;
            }
            throw new SecurityException("Agent " + userId + " is not assigned to conversation " + conversation.getId());
        }
        throw new SecurityException("Role not permitted on chat websocket: " + role);
    }

    private Mono<Void> runSession(WebSocketSession session, Long conversationId, Long userId, String role,
                                   boolean readOnly, long lastMessageId, ConversationStatus conversationStatus) {
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        RegisteredSession registered = new RegisteredSession(session, userId, role, outbound);
        sessionRegistry.register(conversationId, registered);

        // Reconnect replay: every message after the client's last-seen id, oldest first, emitted
        // before any live traffic — no message loss across a reconnect. Tagging replayed frames
        // with the conversation's CURRENT status (not just each message's own historical fields)
        // is what lets a client reconnecting *after* a close (e.g. it missed the live broadcast,
        // or the server restarted) still see conversationStatus=CLOSED on the replayed "Conversation
        // closed." SYSTEM message and leave HUMAN mode — see MessagePayload#conversationStatus.
        Flux<WebSocketMessage> replayFrames = messageRepository
                .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, lastMessageId)
                .map(message -> toMessageEnvelope(message, conversationStatus))
                .map(env -> session.textMessage(writeJson(env)));

        Flux<WebSocketMessage> liveFrames = outbound.asFlux().map(session::textMessage);

        Mono<Void> sendMono = session.send(Flux.concat(replayFrames, liveFrames));

        Mono<Void> receiveMono = session.receive()
                .flatMap(message -> handleIncoming(message.getPayloadAsText(), conversationId, registered, readOnly)
                        .onErrorResume(ex -> {
                            log.error("Error handling WS frame on conversation {}", conversationId, ex);
                            return Mono.empty();
                        }))
                .then();

        // Let the other party know this side just connected. No-ops if nobody else is on the
        // conversation yet (SessionRegistry#broadcastExcludingUser targets an empty set).
        sessionRegistry.broadcastExcludingUser(conversationId, presenceEnvelope(true, role), userId);

        return sendMono.and(receiveMono)
                .doFinally(signal -> {
                    sessionRegistry.deregister(conversationId, registered);
                    sessionRegistry.broadcastExcludingUser(conversationId, presenceEnvelope(false, role), userId);
                });
    }

    private Mono<Void> handleIncoming(String raw, Long conversationId, RegisteredSession self, boolean readOnly) {
        WsEnvelope envelope;
        try {
            envelope = objectMapper.readValue(raw, WsEnvelope.class);
        } catch (Exception ex) {
            sessionRegistry.sendTo(self, errorEnvelope("Malformed frame: " + ex.getMessage()));
            return Mono.empty();
        }
        if (envelope.getType() == null) {
            sessionRegistry.sendTo(self, errorEnvelope("Frame is missing a type"));
            return Mono.empty();
        }

        return switch (envelope.getType()) {
            case "MESSAGE" -> handleMessageFrame(envelope, conversationId, self, readOnly);
            case "TYPING" -> handleTypingFrame(envelope, conversationId, self, readOnly);
            case "READ_RECEIPT" -> handleReadReceiptFrame(envelope, conversationId, self, readOnly);
            case "PRESENCE", "SYSTEM", "ERROR" -> {
                sessionRegistry.sendTo(self, errorEnvelope(
                        "Frame type " + envelope.getType() + " is server-initiated only"));
                yield Mono.empty();
            }
            default -> {
                sessionRegistry.sendTo(self, errorEnvelope("Unknown frame type: " + envelope.getType()));
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleMessageFrame(WsEnvelope envelope, Long conversationId, RegisteredSession self,
                                           boolean readOnly) {
        if (readOnly) {
            sessionRegistry.sendTo(self, errorEnvelope(
                    "This conversation hasn't been accepted yet — you can preview but not reply."));
            return Mono.empty();
        }
        MessagePayload payload;
        try {
            payload = objectMapper.convertValue(envelope.getPayload(), MessagePayload.class);
        } catch (Exception ex) {
            sessionRegistry.sendTo(self, errorEnvelope("Invalid MESSAGE payload"));
            return Mono.empty();
        }
        if (payload == null || payload.getMessage() == null || payload.getMessage().isBlank()) {
            sessionRegistry.sendTo(self, errorEnvelope("Message text is required"));
            return Mono.empty();
        }
        if (payload.getMessage().length() > MAX_MESSAGE_LENGTH) {
            sessionRegistry.sendTo(self, errorEnvelope("Message exceeds " + MAX_MESSAGE_LENGTH + " characters"));
            return Mono.empty();
        }

        // One socket is opened per conversationId (the path variable) — a mismatched
        // conversationId inside the payload is simply ignored, never trusted over the path.
        SenderType senderType = "ADMIN".equals(self.role()) ? SenderType.AGENT : SenderType.CUSTOMER;
        MessageType messageType = parseMessageType(payload.getMessageType());

        return messageService.persistMessage(conversationId, self.userId(), senderType, messageType,
                        payload.getMessage(), null)
                .doOnNext(saved -> sessionRegistry.broadcast(conversationId, WsEnvelope.builder()
                        .type("MESSAGE")
                        .payload(toMessagePayload(saved, payload.getClientMessageId()))
                        .build()))
                .then();
    }

    private Mono<Void> handleTypingFrame(WsEnvelope envelope, Long conversationId, RegisteredSession self,
                                          boolean readOnly) {
        if (readOnly) {
            sessionRegistry.sendTo(self, errorEnvelope("Read-only preview — typing indicator isn't available yet."));
            return Mono.empty();
        }
        TypingPayload payload;
        try {
            payload = objectMapper.convertValue(envelope.getPayload(), TypingPayload.class);
        } catch (Exception ex) {
            sessionRegistry.sendTo(self, errorEnvelope("Invalid TYPING payload"));
            return Mono.empty();
        }
        // Never persisted; forwarded live to the other party only — never echoed to the sender's
        // own other tabs.
        sessionRegistry.broadcastExcludingUser(conversationId,
                WsEnvelope.builder().type("TYPING").payload(payload).build(), self.userId());
        return Mono.empty();
    }

    private Mono<Void> handleReadReceiptFrame(WsEnvelope envelope, Long conversationId, RegisteredSession self,
                                               boolean readOnly) {
        if (readOnly) {
            sessionRegistry.sendTo(self, errorEnvelope("Read-only preview — nothing to mark as read yet."));
            return Mono.empty();
        }
        ReadReceiptPayload payload;
        try {
            payload = objectMapper.convertValue(envelope.getPayload(), ReadReceiptPayload.class);
        } catch (Exception ex) {
            sessionRegistry.sendTo(self, errorEnvelope("Invalid READ_RECEIPT payload"));
            return Mono.empty();
        }
        if (payload == null || payload.getLastReadMessageId() == null) {
            sessionRegistry.sendTo(self, errorEnvelope("lastReadMessageId is required"));
            return Mono.empty();
        }
        SenderType callerSenderType = "ADMIN".equals(self.role()) ? SenderType.AGENT : SenderType.CUSTOMER;
        return messageService.markOtherPartyMessagesRead(conversationId, payload.getLastReadMessageId(), callerSenderType)
                .doOnNext(rows -> sessionRegistry.broadcastExcludingUser(conversationId,
                        WsEnvelope.builder().type("READ_RECEIPT").payload(payload).build(), self.userId()))
                .then();
    }

    private WsEnvelope errorEnvelope(String message) {
        return WsEnvelope.builder().type("ERROR").payload(new ErrorPayload(message)).build();
    }

    private WsEnvelope presenceEnvelope(boolean online, String role) {
        return WsEnvelope.builder().type("PRESENCE").payload(new PresencePayload(online, role)).build();
    }

    private WsEnvelope toMessageEnvelope(ChatMessage message, ConversationStatus conversationStatus) {
        String frameType = message.getSenderType() == SenderType.SYSTEM ? "SYSTEM" : "MESSAGE";
        return WsEnvelope.builder().type(frameType).payload(toMessagePayload(message, null, conversationStatus)).build();
    }

    // conversationStatus defaults to null for ordinary live sends (handleMessageFrame) — the
    // conversation can't have closed mid-send, since a CLOSED conversation is always readOnly
    // (see resolveReadOnly) — and is only meaningfully populated for replay frames, via the
    // 3-arg overload below.
    private MessagePayload toMessagePayload(ChatMessage m, String clientMessageId) {
        return toMessagePayload(m, clientMessageId, null);
    }

    private MessagePayload toMessagePayload(ChatMessage m, String clientMessageId, ConversationStatus conversationStatus) {
        return MessagePayload.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .senderType(m.getSenderType().name())
                .messageType(m.getMessageType().name())
                .message(m.getMessage())
                .attachmentUrl(m.getAttachmentUrl())
                .status(m.getStatus().name())
                .clientMessageId(clientMessageId)
                .conversationStatus(conversationStatus != null ? conversationStatus.name() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String writeJson(WsEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            log.error("Failed to serialize WS envelope", ex);
            return "{\"type\":\"ERROR\",\"payload\":{\"message\":\"internal serialization error\"}}";
        }
    }

    private Long extractConversationId(URI uri) {
        String path = uri.getPath();
        int idx = path.indexOf(PATH_MARKER);
        if (idx < 0) {
            return null;
        }
        String rest = path.substring(idx + PATH_MARKER.length());
        int slash = rest.indexOf('/');
        String idPart = slash >= 0 ? rest.substring(0, slash) : rest;
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long parseLastMessageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private MessageType parseMessageType(String raw) {
        if (raw == null) {
            return MessageType.TEXT;
        }
        try {
            return MessageType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MessageType.TEXT;
        }
    }
}
