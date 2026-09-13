package com.zivdah.common.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageSentEvent {
    private Long conversationId;
    private Long messageId;
    private Long senderId;
    private String senderType;
    private Long recipientUserId;
    private String messagePreview;
}
