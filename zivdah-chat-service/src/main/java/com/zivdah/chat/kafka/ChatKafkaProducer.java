package com.zivdah.chat.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.ChatConversationAcceptedEvent;
import com.zivdah.common.event.ChatConversationClosedEvent;
import com.zivdah.common.event.ChatHumanRequestedEvent;
import com.zivdah.common.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatKafkaProducer {

    // Object-typed (not <String, XxxEvent>) so this one autoconfigured KafkaTemplate bean can
    // carry every event type this producer publishes — Kafka's JSON serializer works from the
    // runtime object regardless of the field's declared generic type anyway.
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishHumanRequested(ChatHumanRequestedEvent event) {
        kafkaTemplate.send(KafkaTopics.CHAT_HUMAN_REQUESTED, event.getConversationId().toString(), event);
    }

    public void publishMessageSent(ChatMessageSentEvent event) {
        kafkaTemplate.send(KafkaTopics.CHAT_MESSAGE_SENT, event.getConversationId().toString(), event);
    }

    public void publishConversationAccepted(ChatConversationAcceptedEvent event) {
        kafkaTemplate.send(KafkaTopics.CHAT_CONVERSATION_ACCEPTED, event.getConversationId().toString(), event);
    }

    public void publishConversationClosed(ChatConversationClosedEvent event) {
        kafkaTemplate.send(KafkaTopics.CHAT_CONVERSATION_CLOSED, event.getConversationId().toString(), event);
    }
}
