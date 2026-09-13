package com.zivdah.chat.chatbot;

import com.zivdah.chat.entity.ChatMessage;

import java.util.List;

// Everything a ChatbotProvider needs to answer one turn without trusting anything the client
// sent beyond the raw message text — customerId/conversationId are always JWT/DB-derived.
// bearerToken is the caller's own validated JWT, forwarded verbatim to the handful of downstream
// endpoints (order-service's /orders/user/{userId} and /orders/cancel/{orderId}, delivery-service's
// /delivery/order/{orderId}) that require an authenticated caller — see OrderServiceClient's and
// DeliveryServiceClient's Javadoc for why this is necessary.
public record ChatContext(
        Long conversationId,
        Long customerId,
        Long orderId,
        String bearerToken,
        List<ChatMessage> recentHistory
) {
}
