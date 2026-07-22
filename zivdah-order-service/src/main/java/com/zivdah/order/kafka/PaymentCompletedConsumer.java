package com.zivdah.order.kafka;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "payment-completed", groupId = "order-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment {} for order {}", event.getStatus(), event.getOrderId());

        Order order = orderRepository.findById(event.getOrderId()).block();
        if (order == null) {
            log.error("Order not found: {}", event.getOrderId());
            return;
        }

        order.setStatus("PAID".equals(event.getStatus()) ? OrderStatus.PAID : OrderStatus.CANCELLED);
        orderRepository.save(order).block();
        log.info("Order {} status updated to {}", event.getOrderId(), order.getStatus());
    }
}
