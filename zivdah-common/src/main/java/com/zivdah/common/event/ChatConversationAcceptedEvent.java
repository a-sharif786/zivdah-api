package com.zivdah.common.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationAcceptedEvent {
    private Long conversationId;
    private Long customerId;
    private Long agentId;
    private String agentDisplayName;
}
