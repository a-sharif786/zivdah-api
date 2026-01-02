package com.zivdah.order.kafka;

import com.zivdah.common.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private static final String TOPIC = "order-created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId().toString(), event);
    }
}