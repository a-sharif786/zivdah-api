package com.zivdah.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    private Long id;

    private String invoiceNumber;

    // UNIQUE + FK to orders(id) at the DB level (see V3 migration) — the hard guarantee behind
    // idempotent invoice generation, not just the app-level findByOrderId check in InvoiceServiceImpl.
    private Long orderId;

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal deliveryFee;
    private BigDecimal tax;
    private BigDecimal totalAmount;

    // "PAID" today — generateInvoice() only ever runs after a PAID transition — kept as a
    // column (not hardcoded) so a future refund/void flow has somewhere to record it.
    private String paymentStatus;
    private String paymentMethod;
    private String transactionId;

    private LocalDateTime invoiceDate;

    // Public (nginx-servable) URL — see InvoiceStorageService. The actual filesystem path is
    // never persisted: it's re-derived from invoice.storage.path + invoiceNumber at read time,
    // so a DB copied between environments (different storage roots) still resolves correctly.
    private String pdfUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
