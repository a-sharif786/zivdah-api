package com.zivdah.notification.kafka;

import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "order-created", groupId = "notification-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Sending order-created notification to user {}", event.getUserId());
        NotificationRequestDto dto = new NotificationRequestDto();
        dto.setUserId(event.getUserId());
        dto.setTitle("Order Placed Successfully");
        dto.setMessage("Your order #" + event.getOrderId() + " has been placed. Total: " + event.getTotalAmount());
        notificationService.sendNotification(dto);
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Sending payment notification to user {}", event.getUserId());
        boolean paid = "PAID".equals(event.getStatus());
        NotificationRequestDto dto = new NotificationRequestDto();
        dto.setUserId(event.getUserId());
        dto.setTitle(paid ? "Payment Successful" : "Payment Failed");
        dto.setMessage(paid
                ? "Payment for order #" + event.getOrderId() + " was successful."
                : "Payment for order #" + event.getOrderId() + " failed. Please retry.");
        notificationService.sendNotification(dto);
    }
}
