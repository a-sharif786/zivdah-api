package com.zivdah.chat.dto;

import com.zivdah.chat.client.dto.OrderSummaryDto;
import com.zivdah.chat.client.dto.ProductSummaryDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotMessageResponseDto {

    private Long conversationId;

    private String reply;

    private List<String> quickReplies;

    // ConversationType name (BOT/HUMAN)
    private String conversationType;

    // ConversationStatus name (OPEN/WAITING/ACTIVE/CLOSED)
    private String conversationStatus;

    private boolean handoffOffered;

    // Populated by PRODUCT_SEARCH — product-service's ProductResponseDto shape verbatim, so the
    // frontend can render a product card with zero adapter code.
    private List<ProductSummaryDto> products;

    // Populated by ORDER_STATUS/CANCEL_ORDER.
    private OrderSummaryDto orderCard;

    // Populated by CANCEL_ORDER's requiresConfirmation flow; resolved via POST /bot/confirm.
    private ConfirmationPromptDto requiresConfirmation;
}
