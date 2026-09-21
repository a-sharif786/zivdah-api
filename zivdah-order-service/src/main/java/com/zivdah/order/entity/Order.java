package com.zivdah.order.entity;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private Long id;

    // Customer
    private Long userId;

    // Order pricing
    private BigDecimal subTotal;

    // Tax details
    private BigDecimal gstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private BigDecimal totalTaxAmount;

    // Additional charges
    private BigDecimal deliveryCharge;
    private BigDecimal packagingCharge;
    private BigDecimal handlingCharge;

    // Discount
    private BigDecimal discountAmount;
    private String couponCode;

    // Final payable amount
    private BigDecimal totalAmount;

    private String currency;

    // Order status
    private OrderStatus status;

    // Delivery address snapshot
    private String deliveryAddressLine1;
    private String deliveryAddressLine2;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryPinCode;
    private String deliveryCountry;

    // Order metadata
    private String orderNumber;

    // Client-generated key for one checkout attempt (see OrderServiceImpl#createOrder) —
    // unique when present, DB-enforced via a partial unique index (V4 migration).
    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}