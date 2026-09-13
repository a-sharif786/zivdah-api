package com.zivdah.chat.chatbot;

import java.util.Set;

// The order-service OrderStatus names a CANCEL_ORDER request may still act on — mirrors
// OrderServiceImpl.ALLOWED_TRANSITIONS' outgoing edges to CANCELLED (order-service enforces this
// same set server-side for its own /orders/{id}/status endpoint, but NOT for
// PUT /orders/cancel/{orderId}, which chat-service calls — so this check has to happen here
// before ever calling cancelOrder). Shared between RuleBasedChatbotProvider (the initial
// requiresConfirmation prompt) and ChatbotServiceImpl (the re-check inside /bot/confirm).
public final class CancellableOrderStatuses {

    public static final Set<String> VALUES = Set.of("CREATED", "PAYMENT_PENDING", "PAID", "CONFIRMED");

    private CancellableOrderStatuses() {
    }
}
