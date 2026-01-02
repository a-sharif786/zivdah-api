package com.zivdah.payment.kafka;

import com.zivdah.payment.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private static final String TOPIC = "payment-events";
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishPaymentEvent(PaymentEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId().toString(), event);
        log.info("Published payment event for order {}: {}", event.getOrderId(), event.getStatus());

    }
}