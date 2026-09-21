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
    // Optional at intent-creation time — the order may not exist yet (see
    // PaymentServiceImpl#initiatePayment). Attached later via PaymentService#linkOrder once the
    // order is actually created.
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;

    // Client-generated, required. Identifies one checkout attempt end-to-end so retrying
    // "Place Order" (same cart) never creates a second Payment row — see
    // PaymentServiceImpl#initiatePayment's checkoutRef lookup.
    private String checkoutRef;

    // Required only when method == UPI — EcomWorldPay's QR API needs these to register the
    // intent (see PaymentServiceImpl#initiatePayment). Ignored for every other method.
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
}
