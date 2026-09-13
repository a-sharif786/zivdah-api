package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

// Server-initiated only — {online, role}. Broadcast to the *other* party's session(s) whenever
// one side connects/disconnects a WS session, and alongside SYSTEM messages on accept/close.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresencePayload {
    private Boolean online;
    private String role;
}
