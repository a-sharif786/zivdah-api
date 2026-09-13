package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadReceiptPayload {
    private Long conversationId;
    private Long lastReadMessageId;
}
