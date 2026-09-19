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

    private String currency;

    private PaymentMethod method;

    private PaymentStatus status;

    private String transactionId;

    private String paymentReference;

    private String gatewayName;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private BigDecimal refundAmount;

    private LocalDateTime refundedAt;

    // Set only when status is FAILED — the payment gateway's own error text (see
    // EcomWorldPayClient/PaymentServiceImpl#registerUpiIntent), shown to the customer verbatim.
    private String failureReason;

    // EcomWorldPay UPI QR (PayIn) fields — see gateway.ecomworldpay package.
    // Present once a UPI intent has been registered — the client renders this as a QR code /
    // "pay via UPI app" link.
    private String upiIntent;
    private String gatewayTxnId;
    private String payerVpa;
    private String rrn;
    private String npciTxnId;
}
