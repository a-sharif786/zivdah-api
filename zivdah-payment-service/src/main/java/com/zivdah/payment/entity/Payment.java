package com.zivdah.payment.entity;
import com.zivdah.payment.enums.PaymentMethod;
import com.zivdah.payment.enums.PaymentStatus;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private Long id;
    // Reference from order-service
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;

    // Stripe/Razorpay transaction id
    private String transactionId;
    private String gatewayResponse;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Gateway details
    private String gatewayName;       // Stripe, Razorpay, PayPal
    private String paymentReference;  // Internal payment reference
    // Failure details
    private String failureReason;
    // Refund
    private String refundTransactionId;
    // Cumulative amount refunded so far (supports topping up a partial refund, capped at `amount`)
    private BigDecimal refundAmount;
    private LocalDateTime refundedAt;

    // EcomWorldPay UPI QR (PayIn) fields — see gateway.ecomworldpay package.
    // Gateway's own transaction id (QR-response "transactionId" == callback/status "pgTxnId"),
    // used as the correlation key for the status-check API. NOT the same as transactionId above,
    // which is our own id and doubles as the invno/invoiceNumber sent to the gateway.
    private String gatewayTxnId;
    private String upiIntent;      // QR string URL returned by the QR API, for the client to render
    private String payerVpa;
    private String rrn;            // Bank reference number / UTR
    private String npciTxnId;

}