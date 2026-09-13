package com.zivdah.chat.chatbot;

import com.zivdah.chat.client.dto.OrderSummaryDto;
import com.zivdah.chat.client.dto.ProductSummaryDto;
import com.zivdah.chat.dto.ConfirmationPromptDto;

import java.util.List;

// Plain reply value produced by a ChatbotProvider for one turn of conversation. products/orderCard
// /requiresConfirmation are the rich attachments Phase 4 intents (PRODUCT_SEARCH, ORDER_STATUS,
// CANCEL_ORDER) populate — all null for the simple canned/text-only replies.
public record BotReply(
        String text,
        List<String> quickReplies,
        boolean requestHandoff,
        String topic,
        List<ProductSummaryDto> products,
        OrderSummaryDto orderCard,
        ConfirmationPromptDto requiresConfirmation
) {
    // Convenience constructor for the common text-only case — keeps every existing 4-arg call
    // site (TALK_TO_HUMAN, REFUND_REQUEST, CHANGE_ADDRESS, the UNKNOWN fallback, etc.) unchanged.
    public BotReply(String text, List<String> quickReplies, boolean requestHandoff, String topic) {
        this(text, quickReplies, requestHandoff, topic, null, null, null);
    }
}
