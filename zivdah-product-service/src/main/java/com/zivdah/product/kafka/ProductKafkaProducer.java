package com.zivdah.product.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductKafkaProducer {

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    public void publishProductCreated(ProductCreatedEvent event) {
        kafkaTemplate.send(KafkaTopics.PRODUCT_CREATED, event.getProductId().toString(), event);
    }
}
