package com.zivdah.chat.config;

import com.zivdah.chat.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

// Registers ChatWebSocketHandler at /ws/chat/** — a native reactive WebSocketHandler, not
// STOMP/SockJS (this platform has no servlet-based STOMP broker available; see the plan's
// architecture decision #3). High precedence (-1) so this mapping is consulted before the
// framework's default RequestMappingHandlerMapping for annotated @RestControllers.
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping chatWebSocketMapping(ChatWebSocketHandler handler) {
        Map<String, WebSocketHandler> map = Map.of("/ws/chat/**", handler);
        return new SimpleUrlHandlerMapping(map, -1);
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
