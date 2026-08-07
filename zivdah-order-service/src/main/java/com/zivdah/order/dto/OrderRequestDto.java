package com.zivdah.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequestDto {
    private Long userId;

    // Pricing
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

    // Final amount
    private BigDecimal totalAmount;

    private String currency;


    // Address
    private String deliveryAddressLine1;
    private String deliveryAddressLine2;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryPinCode;
    private String deliveryCountry;


    private List<OrderItemDto> items;
}