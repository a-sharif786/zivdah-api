package com.zivdah.payment.kafka;


import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.payment.event.PaymentEvent;
import com.zivdah.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    //private final PaymentKafkaProducer kafkaProducer;
    private final PaymentService paymentService; // your business logic service

    @KafkaListener(topics = "order-created", groupId = "payment-group")
    public void consumeOrderCreated(OrderCreatedEvent event) {
        log.info("Received order {} for payment", event.getOrderId());

        // Process payment
     //   boolean success = paymentService.processPayment(event.getOrderId(), event.getTotalAmount());

        // Send payment result
//        PaymentEvent paymentEvent = PaymentEvent.builder()
//                .orderId(event.getOrderId())
//                .status(success ? "PAID" : "FAILED")
//                .build();
//
//        kafkaProducer.publishPaymentEvent(paymentEvent);
    }
}
