package com.zivdah.delivery.kafka;

import com.zivdah.common.event.OrderStatusChangedEvent;
import com.zivdah.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final DeliveryService deliveryService;

    // Same topic zivdah-order-service publishes order-level transitions on, and this
    // service's own transitions publish sub-statuses on too (see DeliveryKafkaProducer) —
    // filter down to the one transition that matters here: an order becoming ready for
    // fulfillment, at which point one PENDING Delivery is created per vendor on the order.
    @KafkaListener(topics = "order-status-changed", groupId = "delivery-group")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (!"CONFIRMED".equals(event.getNewStatus())) {
            return;
        }
        log.info("Order {} confirmed — creating pending deliveries", event.getOrderId());
        deliveryService.createPendingDeliveriesForOrder(event.getOrderId(), event.getUserId()).block();
    }
}
