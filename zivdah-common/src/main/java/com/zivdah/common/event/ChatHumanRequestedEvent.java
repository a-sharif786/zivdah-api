package com.zivdah.common.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHumanRequestedEvent {
    private Long conversationId;
    private Long customerId;
    private Long orderId;
    private String topic;
}
