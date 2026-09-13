package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

// Server-initiated only — sent back to a single offending session (never broadcast) when it
// sends a malformed frame, an unpersisted client-only frame type, or a MESSAGE while read-only.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorPayload {
    private String message;
}
