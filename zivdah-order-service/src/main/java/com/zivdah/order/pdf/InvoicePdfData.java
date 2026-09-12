package com.zivdah.order.pdf;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Everything InvoicePdfGenerator needs, already resolved — kept separate from the Invoice
// entity/DTO so the PDF layout code doesn't reach back into repositories or other services.
@Getter
@Builder
public class InvoicePdfData {
    private final String invoiceNumber;
    private final LocalDateTime invoiceDate;

    private final String orderNumber;
    private final Long orderId;

    private final String customerName;
    private final String customerEmail;
    private final String customerMobile;

    private final String addressLine1;
    private final String addressLine2;
    private final String city;
    private final String state;
    private final String pinCode;
    private final String country;

    private final List<LineItem> items;

    private final BigDecimal subtotal;
    private final BigDecimal discount;
    private final BigDecimal deliveryFee;
    private final BigDecimal tax;
    private final BigDecimal totalAmount;
    private final String currency;

    private final String paymentStatus;
    private final String paymentMethod;
    private final String transactionId;

    @Getter
    @Builder
    public static class LineItem {
        private final String productName;
        private final int quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal discount;
        private final BigDecimal itemTotal;
    }
}
