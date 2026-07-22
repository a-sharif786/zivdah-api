package com.zivdah.order.kafka;

import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.order.entity.Order;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "payment-completed", groupId = "order-group")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment {} for order {}", event.getStatus(), event.getOrderId());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderId()));

        if ("PAID".equals(event.getStatus())) {
            order.setStatus(OrderStatus.PAID);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
        }

        orderRepository.save(order);
        log.info("Order {} status updated to {}", event.getOrderId(), order.getStatus());
    }
}
