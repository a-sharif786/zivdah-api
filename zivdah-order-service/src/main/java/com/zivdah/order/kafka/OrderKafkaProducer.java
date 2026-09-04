package com.zivdah.order.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private static final String TOPIC = "order-created";

    // Object-typed (not <String, OrderCreatedEvent>) so this one autoconfigured KafkaTemplate
    // bean can carry both event types this producer now publishes — Kafka's JSON serializer
    // works from the runtime object regardless of the field's declared generic type anyway.
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId().toString(), event);
    }

    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGED, event.getOrderId().toString(), event);
    }
}