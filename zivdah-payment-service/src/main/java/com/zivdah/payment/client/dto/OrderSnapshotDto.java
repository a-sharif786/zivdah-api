package com.zivdah.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

// The subset of order-service's OrderResponseDto that PaymentServiceImpl#linkOrder checks a
// payment against. status is order-service's OrderStatus name as a String.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderSnapshotDto {
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
}
