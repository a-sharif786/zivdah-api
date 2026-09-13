package com.zivdah.chat.websocket;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

// One live WebSocket connection registered under a conversationId — bundles the raw session with
// the caller identity (needed to filter "other party" broadcasts for TYPING/READ_RECEIPT/
// PRESENCE) and its dedicated outbound sink. A reactive WebSocketSession's send() is meant to be
// subscribed exactly once per session (in ChatWebSocketHandler#runSession); SessionRegistry pushes
// broadcast frames into this sink rather than ever calling session.send() again itself.
public record RegisteredSession(WebSocketSession session, Long userId, String role, Sinks.Many<String> outbound) {
}
