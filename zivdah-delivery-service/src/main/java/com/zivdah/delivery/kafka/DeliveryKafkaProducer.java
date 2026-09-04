package com.zivdah.delivery.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.DeliveryAssignedEvent;
import com.zivdah.common.event.DeliveryCompletedEvent;
import com.zivdah.common.event.DeliveryFailedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryKafkaProducer {

    // Object-typed so this one autoconfigured KafkaTemplate bean can carry every event type
    // this producer publishes — same pattern as zivdah-order-service's OrderKafkaProducer.
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishDeliveryAssigned(DeliveryAssignedEvent event) {
        kafkaTemplate.send(KafkaTopics.DELIVERY_ASSIGNED, event.getOrderId().toString(), event);
    }

    // PACKED/READY_FOR_PICKUP/PICKED_UP/ON_THE_WAY/CANCELLED — reuses the same topic/event
    // shape order-service already publishes order-level transitions on, so
    // zivdah-notification-service's existing order-status-changed consumer handles both with
    // one handler (see OrderStatusChangedEvent's vendorId/deliveryBoyId fields).
    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGED, event.getOrderId().toString(), event);
    }

    public void publishDeliveryFailed(DeliveryFailedEvent event) {
        kafkaTemplate.send(KafkaTopics.DELIVERY_FAILED, event.getOrderId().toString(), event);
    }

    public void publishDeliveryCompleted(DeliveryCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.DELIVERY_COMPLETED, event.getOrderId().toString(), event);
    }
}
