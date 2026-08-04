package com.zivdah.order.dto;

import com.zivdah.order.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDto {

    private Long orderId;
    private String orderNumber;

    private Long userId;


    private BigDecimal subTotal;


    // Tax
    private BigDecimal gstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;


    // Charges
    private BigDecimal deliveryCharge;
    private BigDecimal packagingCharge;
    private BigDecimal handlingCharge;


    // Discount
    private BigDecimal discountAmount;
    private String couponCode;


    private BigDecimal totalAmount;

    private String currency;


    private OrderStatus status;


    private String deliveryAddressLine1;
    private String deliveryAddressLine2;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryPinCode;
    private String deliveryCountry;


    private List<OrderItemDto> items;


    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}