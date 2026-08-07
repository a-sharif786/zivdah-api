package com.zivdah.payment.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, event.getOrderId().toString(), event);
        log.info("Published payment-completed event for order {}: {}", event.getOrderId(), event.getStatus());
    }
}