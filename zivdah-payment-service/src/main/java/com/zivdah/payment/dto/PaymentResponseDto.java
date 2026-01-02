package com.zivdah.payment.dto;

import com.zivdah.payment.enums.PaymentMethod;
import com.zivdah.payment.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionId;
    private LocalDateTime createdAt;
}
