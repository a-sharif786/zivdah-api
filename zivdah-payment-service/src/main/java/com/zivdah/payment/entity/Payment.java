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

}