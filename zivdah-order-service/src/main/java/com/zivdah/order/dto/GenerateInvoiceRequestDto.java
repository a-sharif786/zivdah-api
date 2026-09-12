package com.zivdah.order.dto;

import lombok.*;

import java.time.LocalDateTime;

// Body for POST /invoices/generate/{orderId}. Every field is optional: the automatic hook from
// updatePaymentStatus() supplies paymentMethod/transactionId/paidAt from the Payment record
// itself (see PaymentServiceImpl#markPaymentSuccess -> OrderServiceClient); a manual
// admin/customer-triggered regeneration (force=true, e.g. after a PDF was somehow lost) may not
// have those at hand, in which case the existing invoice's own values are kept, or generation-time
// defaults are used for a first-time manual generate.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateInvoiceRequestDto {
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime paidAt;

    // If an invoice already exists for the order, generateInvoice() just returns it — pass
    // force=true to regenerate the PDF and overwrite the stored file (idempotency requirement).
    @Builder.Default
    private boolean force = false;
}
