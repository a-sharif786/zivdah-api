package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

// Generic WebSocket frame envelope: {"type": "...", "payload": {...}}. `type` is one of
// MESSAGE|TYPING|READ_RECEIPT|PRESENCE|SYSTEM|ERROR (see ChatWebSocketHandler). `payload` is left
// as a raw Object rather than a sealed hierarchy since each frame type carries a different shape;
// ChatWebSocketHandler converts it to the matching small payload POJO
// (MessagePayload/TypingPayload/ReadReceiptPayload/PresencePayload/ErrorPayload) once it knows the
// type, via ObjectMapper#convertValue. For outbound frames `payload` just holds the POJO directly
// — Jackson serializes an Object-typed field using its runtime type.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class WsEnvelope {
    private String type;
    private Object payload;
}
