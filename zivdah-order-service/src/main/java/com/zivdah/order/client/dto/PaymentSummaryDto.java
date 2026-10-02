package com.zivdah.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

// The subset of payment-service's PaymentResponseDto that PaymentCompletedConsumer needs to
// verify a payment-completed event. status is payment-service's PaymentStatus name as a String.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSummaryDto {
    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private String status;
}
