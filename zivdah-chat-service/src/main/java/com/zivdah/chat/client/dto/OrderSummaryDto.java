package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Narrow projection of order-service's OrderResponseDto — only the fields ORDER_STATUS,
// CANCEL_ORDER and ORDER_HISTORY actually need, plus this doubles as the `orderCard` shape on
// BotMessageResponseDto so the frontend can render an order card straight from this DTO with no
// adapter. `userId` is required (even though the frontend never needs to display it) because
// order-service enforces NO ownership check on GET /orders/{orderId}, GET /orders/user/{userId}
// or PUT /orders/cancel/{orderId} — RuleBasedChatbotProvider/ChatbotServiceImpl compare this
// against the JWT-derived customerId before ever using or acting on the result (see
// OrderServiceClient's Javadoc for the exact gap).
@Getter
@Setter
public class OrderSummaryDto {
    private Long orderId;
    private String orderNumber;
    private Long userId;
    // Raw OrderStatus enum name as a String (e.g. "CREATED", "CONFIRMED", "CANCELLED") — kept
    // as a plain String rather than order-service's own enum since chat-service has no shared
    // dependency on order-service's code; the cancellable-status check compares against a
    // Set<String> of the same names instead.
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String deliveryAddressLine1;
    private String deliveryCity;
    private LocalDateTime createdAt;
}
