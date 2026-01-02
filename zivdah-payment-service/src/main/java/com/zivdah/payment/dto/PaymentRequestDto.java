package com.zivdah.payment.dto;

import com.zivdah.payment.enums.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDto {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod method;
}
