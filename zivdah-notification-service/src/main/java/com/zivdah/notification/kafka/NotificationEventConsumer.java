package com.zivdah.notification.kafka;

import com.zivdah.common.event.ChatConversationAcceptedEvent;
import com.zivdah.common.event.ChatConversationClosedEvent;
import com.zivdah.common.event.ChatHumanRequestedEvent;
import com.zivdah.common.event.ChatMessageSentEvent;
import com.zivdah.common.event.DeliveryAssignedEvent;
import com.zivdah.common.event.DeliveryCompletedEvent;
import com.zivdah.common.event.DeliveryFailedEvent;
import com.zivdah.common.event.OrderCreatedEvent;
import com.zivdah.common.event.OrderStatusChangedEvent;
import com.zivdah.common.event.PaymentCompletedEvent;
import com.zivdah.notification.client.AuthServiceClient;
import com.zivdah.notification.client.OrderServiceClient;
import com.zivdah.notification.dto.NotificationRequestDto;
import com.zivdah.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final OrderServiceClient orderServiceClient;
    private final AuthServiceClient authServiceClient;

    @KafkaListener(topics = "order-created", groupId = "notification-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Sending order-created notifications for order {}", orderId);

        notifyOne(event.getUserId(), "USER", orderId, "Order Placed Successfully",
                "Your order #" + orderId + " has been placed. Total: " + event.getTotalAmount(),
                "ORDER_CREATED", null);

        notifyMany(orderServiceClient.getVendorIds(orderId).block(), "VENDOR", orderId, "New Order Received",
                "You have a new order #" + orderId + " to process.", "ORDER_CREATED", null);

        notifyAdmins(orderId, "New Order Created", "A new order #" + orderId + " has been created.",
                "ORDER_CREATED", null);
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Sending payment notification for order {}", orderId);
        boolean paid = "PAID".equals(event.getStatus());

        notifyOne(event.getUserId(), "USER", orderId,
                paid ? "Payment Successful" : "Payment Failed",
                paid ? "Payment for order #" + orderId + " was successful."
                        : "Payment for order #" + orderId + " failed. Please retry.",
                "PAYMENT_COMPLETED", null);

        // Vendor is only notified on success — a failed payment is the customer's problem to
        // retry, nothing for the vendor to act on yet.
        if (paid) {
            notifyMany(orderServiceClient.getVendorIds(orderId).block(), "VENDOR", orderId, "Payment Received",
                    "Payment received for order #" + orderId + ". Please process the order.",
                    "PAYMENT_COMPLETED", null);
        }
    }

    // Carries BOTH order-level transitions (published by zivdah-order-service — vendorId/
    // deliveryBoyId are null on those) and delivery sub-status transitions (published by
    // zivdah-delivery-service — vendorId is always set, deliveryBoyId once assigned). One
    // topic, one event shape, branched on newStatus.
    @KafkaListener(topics = "order-status-changed", groupId = "notification-group")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Long orderId = event.getOrderId();
        Long customerId = event.getUserId();
        String newStatus = event.getNewStatus();
        log.info("Order {} status changed {} -> {} (by {} #{})",
                orderId, event.getOldStatus(), newStatus, event.getChangedByRole(), event.getChangedByUserId());

        switch (newStatus) {
            // --- order-level transitions ---
            case "CONFIRMED" -> {
                notifyOne(customerId, "USER", orderId, "Order Accepted", "Your order #" + orderId + " has been accepted.", "ORDER_STATUS_CHANGED", newStatus);
                notifyAdmins(orderId, "Order Accepted", "Order #" + orderId + " has been accepted by the vendor.", "ORDER_STATUS_CHANGED", newStatus);
            }
            case "CANCELLED" -> {
                boolean vendorRejected = "VENDOR".equalsIgnoreCase(event.getChangedByRole());
                if (vendorRejected) {
                    // The vendor is the actor here — don't notify them about their own action.
                    notifyOne(customerId, "USER", orderId, "Order Rejected", "Your order #" + orderId + " has been rejected.", "ORDER_STATUS_CHANGED", newStatus);
                    notifyAdmins(orderId, "Order Rejected", "Order #" + orderId + " was rejected by the vendor.", "ORDER_STATUS_CHANGED", newStatus);
                } else {
                    notifyOne(customerId, "USER", orderId, "Order Cancelled", "Your order #" + orderId + " has been cancelled.", "ORDER_STATUS_CHANGED", newStatus);
                    notifyMany(orderServiceClient.getVendorIds(orderId).block(), "VENDOR", orderId, "Order Cancelled", "Order #" + orderId + " has been cancelled.", "ORDER_STATUS_CHANGED", newStatus);
                    notifyAdmins(orderId, "Order Cancelled", "Order #" + orderId + " has been cancelled.", "ORDER_STATUS_CHANGED", newStatus);
                }
            }
            case "PACKING" ->
                    notifyOne(customerId, "USER", orderId, "Order Packed", "Your order #" + orderId + " has been packed.", "ORDER_STATUS_CHANGED", newStatus);
            case "OUT_FOR_DELIVERY" ->
                    notifyOne(customerId, "USER", orderId, "Order Shipped", "Your order #" + orderId + " is on the way.", "ORDER_STATUS_CHANGED", newStatus);
            case "DELIVERED" -> {
                notifyOne(customerId, "USER", orderId, "Order Delivered", "Your order #" + orderId + " has been delivered successfully.", "ORDER_STATUS_CHANGED", newStatus);
                notifyMany(orderServiceClient.getVendorIds(orderId).block(), "VENDOR", orderId, "Order Delivered", "Order #" + orderId + " has been delivered successfully.", "ORDER_STATUS_CHANGED", newStatus);
            }
            case "REFUNDED" -> {
                notifyOne(customerId, "USER", orderId, "Refund Completed", "Refund for your order #" + orderId + " has been completed.", "ORDER_STATUS_CHANGED", newStatus);
                notifyMany(orderServiceClient.getVendorIds(orderId).block(), "VENDOR", orderId, "Refund Completed", "Refund for order #" + orderId + " has been completed.", "ORDER_STATUS_CHANGED", newStatus);
                notifyAdmins(orderId, "Refund Completed", "Refund for order #" + orderId + " has been completed.", "ORDER_STATUS_CHANGED", newStatus);
            }

            // --- delivery sub-statuses (see zivdah-delivery-service.DeliveryServiceImpl) —
            // customer + admin + the owning vendor, never the delivery boy who acted.
            case "PACKED" -> notifyDeliveryProgress(event, "Order Packed", "Your order #" + orderId + " has been packed by the vendor.");
            case "READY_FOR_PICKUP" -> notifyDeliveryProgress(event, "Ready for Pickup", "Order #" + orderId + " is ready for pickup.");
            case "PICKED_UP" -> notifyDeliveryProgress(event, "Order Picked Up", "Your order #" + orderId + " has been picked up for delivery.");
            case "ON_THE_WAY" -> notifyDeliveryProgress(event, "Order On The Way", "Your order #" + orderId + " is on the way.");
            case "DELIVERY_CANCELLED" -> notifyDeliveryProgress(event, "Delivery Cancelled", "The delivery for order #" + orderId + " was cancelled.");

            default -> log.debug("No notification configured for order {} -> status {}", orderId, newStatus);
        }
    }

    @KafkaListener(topics = "delivery-assigned", groupId = "notification-group")
    public void onDeliveryAssigned(DeliveryAssignedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Delivery {} for order {} assigned to delivery boy {}", event.getDeliveryId(), orderId, event.getDeliveryBoyId());

        notifyOne(event.getDeliveryBoyId(), "DELIVERY_BOY", orderId, "New Delivery Assigned",
                "You've been assigned to deliver order #" + orderId + ".", "DELIVERY_ASSIGNED", null);
        notifyOne(event.getUserId(), "USER", orderId, "Delivery Boy Assigned",
                "A delivery partner has been assigned to your order #" + orderId + ".", "DELIVERY_ASSIGNED", null);

        boolean vendorIsActor = "VENDOR".equalsIgnoreCase(event.getAssignedByRole())
                && event.getVendorId() != null && event.getVendorId().equals(event.getAssignedByUserId());
        if (!vendorIsActor) {
            notifyOne(event.getVendorId(), "VENDOR", orderId, "Delivery Boy Assigned",
                    "A delivery partner has been assigned to order #" + orderId + ".", "DELIVERY_ASSIGNED", null);
        }
        notifyAdmins(orderId, "Delivery Boy Assigned",
                "A delivery partner has been assigned to order #" + orderId + ".", "DELIVERY_ASSIGNED", null);
    }

    @KafkaListener(topics = "delivery-failed", groupId = "notification-group")
    public void onDeliveryFailed(DeliveryFailedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Delivery {} for order {} failed: {}", event.getDeliveryId(), orderId, event.getFailureReason());
        String message = "Delivery for order #" + orderId + " failed"
                + (event.getFailureReason() != null ? " (" + event.getFailureReason() + ")" : "") + ".";

        // Deliberately not the delivery boy who performed the action.
        notifyOne(event.getUserId(), "USER", orderId, "Delivery Failed", message, "DELIVERY_FAILED", null);
        notifyOne(event.getVendorId(), "VENDOR", orderId, "Delivery Failed", message, "DELIVERY_FAILED", null);
        notifyAdmins(orderId, "Delivery Failed", message, "DELIVERY_FAILED", null);
    }

    @KafkaListener(topics = "delivery-completed", groupId = "notification-group")
    public void onDeliveryCompleted(DeliveryCompletedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Delivery {} for order {} completed", event.getDeliveryId(), orderId);

        notifyOne(event.getUserId(), "USER", orderId, "Order Delivered",
                "Your order #" + orderId + " has been delivered successfully.", "DELIVERY_COMPLETED", null);
        notifyOne(event.getVendorId(), "VENDOR", orderId, "Order Delivered",
                "Order #" + orderId + " has been delivered successfully.", "DELIVERY_COMPLETED", null);
        notifyAdmins(orderId, "Order Delivered",
                "Order #" + orderId + " has been delivered successfully.", "DELIVERY_COMPLETED", null);
    }

    // --- zivdah-chat-service events (Phase 5) ---
    // Chat-service has no visibility into which specific ADMIN accounts are enabled as support
    // agents (that roster is chat-service-only) — so, like order-created's own notifyAdmins(),
    // this fans out to every ADMIN account rather than a narrower "on-duty agents" set.
    @KafkaListener(topics = "chat-human-requested", groupId = "notification-group")
    public void onChatHumanRequested(ChatHumanRequestedEvent event) {
        Long conversationId = event.getConversationId();
        log.info("Chat conversation {} requested human support (topic={})", conversationId, event.getTopic());
        List<Long> adminIds = authServiceClient.getAdminUserIds().block();
        if (adminIds == null || adminIds.isEmpty()) return;
        String message = "A customer needs help"
                + (event.getTopic() != null ? " with " + event.getTopic().toLowerCase() : "") + ".";
        notificationService.sendToMany(adminIds, "New support request", message, "ADMIN",
                        "CHAT_HUMAN_REQUESTED", "CHAT_CONVERSATION", conversationId,
                        "CHAT_HUMAN_REQUESTED:" + conversationId)
                .blockLast();
    }

    @KafkaListener(topics = "chat-conversation-accepted", groupId = "notification-group")
    public void onChatConversationAccepted(ChatConversationAcceptedEvent event) {
        Long conversationId = event.getConversationId();
        log.info("Chat conversation {} accepted by agent {}", conversationId, event.getAgentId());
        String agentName = event.getAgentDisplayName() != null ? event.getAgentDisplayName() : "A support agent";
        notifyOneForChat(event.getCustomerId(), "USER", conversationId, "An agent has joined your chat",
                agentName + " is now helping you.", "CHAT_CONVERSATION_ACCEPTED", null);
    }

    @KafkaListener(topics = "chat-message-sent", groupId = "notification-group")
    public void onChatMessageSent(ChatMessageSentEvent event) {
        // Best-effort push fallback for when the recipient's socket isn't open — the WebSocket
        // already delivers it live if connected. Skip entirely if there's no one to notify yet
        // (e.g. a customer message on a conversation nobody has accepted).
        if (event.getRecipientUserId() == null) return;
        String recipientRole = "AGENT".equals(event.getSenderType()) ? "USER" : "ADMIN";
        // messageId as the dedup discriminator: unlike the one-time accept/close/handoff events,
        // many messages legitimately share the same conversationId+recipient, so the plain key
        // would silently drop every message after the first (see the class-level dedupKey note).
        notifyOneForChat(event.getRecipientUserId(), recipientRole, event.getConversationId(), "New message",
                event.getMessagePreview(), "CHAT_MESSAGE_SENT", String.valueOf(event.getMessageId()));
    }

    @KafkaListener(topics = "chat-conversation-closed", groupId = "notification-group")
    public void onChatConversationClosed(ChatConversationClosedEvent event) {
        Long conversationId = event.getConversationId();
        log.info("Chat conversation {} closed (resolved={})", conversationId, event.isResolved());
        notifyOneForChat(event.getCustomerId(), "USER", conversationId, "Chat closed",
                event.isResolved()
                        ? "Your support chat has been resolved. Please rate your experience."
                        : "Your support chat has ended.",
                "CHAT_CONVERSATION_CLOSED", null);
    }

    private void notifyOneForChat(Long recipientUserId, String recipientRole, Long conversationId, String title,
                                   String message, String notificationType, String dedupDiscriminator) {
        if (recipientUserId == null) return;
        NotificationRequestDto dto = new NotificationRequestDto();
        dto.setUserId(recipientUserId);
        dto.setTitle(title);
        dto.setMessage(message);
        dto.setRecipientRole(recipientRole);
        dto.setNotificationType(notificationType);
        dto.setEntityType("CHAT_CONVERSATION");
        dto.setEntityId(conversationId);
        dto.setDedupKey(notificationType + ":" + conversationId
                + (dedupDiscriminator != null ? ":" + dedupDiscriminator : "") + ":" + recipientUserId);
        notificationService.sendNotification(dto).block();
    }

    // Customer + admin + the owning vendor — deliberately never the delivery boy who
    // performed the action (same "don't notify the actor" rule used above).
    private void notifyDeliveryProgress(OrderStatusChangedEvent event, String title, String message) {
        Long orderId = event.getOrderId();
        notifyOne(event.getUserId(), "USER", orderId, title, message, "ORDER_STATUS_CHANGED", event.getNewStatus());
        boolean vendorIsActor = "VENDOR".equalsIgnoreCase(event.getChangedByRole())
                && event.getVendorId() != null && event.getVendorId().equals(event.getChangedByUserId());
        if (!vendorIsActor) {
            notifyOne(event.getVendorId(), "VENDOR", orderId, title, message, "ORDER_STATUS_CHANGED", event.getNewStatus());
        }
        notifyAdmins(orderId, title, message, "ORDER_STATUS_CHANGED", event.getNewStatus());
    }

    // dedupDiscriminator (e.g. the specific status a transition landed on) is folded into
    // the dedup key alongside notificationType+orderId+recipient — without it, every
    // ORDER_STATUS_CHANGED notification for the same order/user would collide on the same
    // key and every transition after the first would be silently dropped as a "duplicate".
    private void notifyOne(Long recipientUserId, String recipientRole, Long orderId, String title, String message,
                            String notificationType, String dedupDiscriminator) {
        if (recipientUserId == null) return;
        NotificationRequestDto dto = new NotificationRequestDto();
        dto.setUserId(recipientUserId);
        dto.setTitle(title);
        dto.setMessage(message);
        dto.setRecipientRole(recipientRole);
        dto.setNotificationType(notificationType);
        dto.setEntityType("ORDER");
        dto.setEntityId(orderId);
        dto.setDedupKey(dedupKey(notificationType, orderId, dedupDiscriminator, recipientUserId));
        notificationService.sendNotification(dto).block();
    }

    private void notifyMany(List<Long> recipientUserIds, String recipientRole, Long orderId, String title, String message,
                             String notificationType, String dedupDiscriminator) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) return;
        String prefix = notificationType + ":" + orderId + (dedupDiscriminator != null ? ":" + dedupDiscriminator : "");
        notificationService.sendToMany(recipientUserIds, title, message, recipientRole, notificationType, "ORDER", orderId, prefix)
                .blockLast();
    }

    private void notifyAdmins(Long orderId, String title, String message, String notificationType, String dedupDiscriminator) {
        notifyMany(authServiceClient.getAdminUserIds().block(), "ADMIN", orderId, title, message, notificationType, dedupDiscriminator);
    }

    private String dedupKey(String notificationType, Long orderId, String discriminator, Long recipientUserId) {
        return notificationType + ":" + orderId
                + (discriminator != null ? ":" + discriminator : "")
                + ":" + recipientUserId;
    }
}
