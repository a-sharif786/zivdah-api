package com.zivdah.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDto {
    private Long id;
    private String invoiceNumber;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private String customerEmail;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal deliveryFee;
    private BigDecimal tax;
    private BigDecimal totalAmount;

    private String paymentStatus;
    private String paymentMethod;
    private String transactionId;

    private LocalDateTime invoiceDate;
    private String pdfUrl;
    // Same-origin, authenticated path that validates ownership before streaming the PDF —
    // the one the frontend's "View/Download Invoice" buttons should call, not pdfUrl directly
    // (pdfUrl is nginx-static and unauthenticated by nature — see the nginx config notes).
    private String downloadUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
