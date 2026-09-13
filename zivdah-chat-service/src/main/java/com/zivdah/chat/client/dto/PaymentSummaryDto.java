package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Narrow projection of payment-service's PaymentResponseDto. Not read by any Phase 4 intent
// today (REFUND_REQUEST never calls a payment endpoint — see RuleBasedChatbotProvider) — kept
// narrow now so a future order-context aggregation panel can reuse it without inventing a new
// shape (see PaymentServiceClient's Javadoc).
@Getter
@Setter
public class PaymentSummaryDto {
    private Long paymentId;
    private Long orderId;
    private String status;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
}
