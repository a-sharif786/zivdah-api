package com.zivdah.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.chat.dto.ws.WsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

// In-memory registry of live human-chat WebSocket sessions, keyed by conversationId. Deliberately
// not backed by a DB/Redis: agent presence is a known single-instance limitation for now (see the
// plan's "Agent presence is in-memory only" note) — fine until this service is ever scaled
// horizontally, at which point this would move to Redis pub/sub.
//
// Broadcasting here is synchronous and non-blocking: it only serializes the envelope once and
// pushes the JSON string into each target session's own Sinks.Many (see RegisteredSession) — the
// actual network write happens later when ChatWebSocketHandler's session.send() drains that sink.
// That's what makes it safe to call broadcast*() from anywhere, including from inside a
// Mono.doFinally callback (WS disconnect cleanup) where nothing downstream could subscribe to a
// returned Mono.
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionRegistry {

    private final ObjectMapper objectMapper;

    private final Map<Long, Set<RegisteredSession>> sessionsByConversation = new ConcurrentHashMap<>();

    public void register(Long conversationId, RegisteredSession registered) {
        sessionsByConversation.computeIfAbsent(conversationId, id -> new CopyOnWriteArraySet<>()).add(registered);
    }

    public void deregister(Long conversationId, RegisteredSession registered) {
        sessionsByConversation.computeIfPresent(conversationId, (id, set) -> {
            set.remove(registered);
            return set.isEmpty() ? null : set;
        });
    }

    // Sends to every session registered for this conversation — used for MESSAGE/SYSTEM frames,
    // which every party (including the sender's own other tabs) should receive.
    public void broadcast(Long conversationId, WsEnvelope envelope) {
        emit(sessionsByConversation.getOrDefault(conversationId, Set.of()), envelope);
    }

    // Sends to every session registered for this conversation EXCEPT ones belonging to
    // excludeUserId — used for TYPING, READ_RECEIPT, and connect/disconnect PRESENCE, none of
    // which the sender needs echoed back to its own tab(s).
    public void broadcastExcludingUser(Long conversationId, WsEnvelope envelope, Long excludeUserId) {
        Set<RegisteredSession> targets = sessionsByConversation.getOrDefault(conversationId, Set.of()).stream()
                .filter(s -> !s.userId().equals(excludeUserId))
                .collect(Collectors.toSet());
        emit(targets, envelope);
    }

    // Sends only to one session — used for ERROR frames rejecting a single client's bad/forbidden
    // frame; no other party on the conversation should see it.
    public void sendTo(RegisteredSession target, WsEnvelope envelope) {
        emit(Set.of(target), envelope);
    }

    private void emit(Set<RegisteredSession> targets, WsEnvelope envelope) {
        if (targets.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WS envelope type={}", envelope.getType(), e);
            return;
        }
        for (RegisteredSession target : targets) {
            Sinks.EmitResult result = target.outbound().tryEmitNext(json);
            if (result.isFailure()) {
                log.warn("Failed to enqueue WS frame for session {} (type={}, result={})",
                        target.session().getId(), envelope.getType(), result);
            }
        }
    }
}
