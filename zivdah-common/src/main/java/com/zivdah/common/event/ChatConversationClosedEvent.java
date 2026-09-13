package com.zivdah.common.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationClosedEvent {
    private Long conversationId;
    private Long customerId;
    private Long agentId;
    private boolean resolved;
}
