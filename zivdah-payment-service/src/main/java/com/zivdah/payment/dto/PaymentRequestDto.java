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
    private String currency;
    private PaymentMethod method;

    // Required only when method == UPI — EcomWorldPay's QR API needs these to register the
    // intent (see PaymentServiceImpl#initiatePayment). Ignored for every other method.
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
}
