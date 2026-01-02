package com.zivdah.payment.entity;

import com.zivdah.payment.enums.PaymentMethod;
import com.zivdah.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private Long userId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String transactionId;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        status = PaymentStatus.INITIATED;
    }
}
