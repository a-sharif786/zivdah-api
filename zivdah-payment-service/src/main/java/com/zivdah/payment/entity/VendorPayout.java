package com.zivdah.payment.entity;

import com.zivdah.payment.enums.VendorPayoutStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A vendor's request to withdraw earnings to their bank account/UPI VPA via EcomWorldPay's
// Payout Payment API. Deliberately its own table rather than reusing Payment — every Payment
// query/aggregate in this service assumes an order-backed row (see PaymentRepository), and a
// payout has no orderId at all.
@Table("vendor_payouts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPayout {

    @Id
    private Long id;
    private Long vendorId;
    private BigDecimal amount;

    // Snapshotted from the vendor's profile at request time (see AuthServiceClient) so a
    // later profile edit can't retroactively change a request already awaiting approval.
    private String payoutMode; // "UPI" or "IMPS"
    private String accountNo;
    private String ifscBankCode;
    private String payeeVpa;

    private VendorPayoutStatus status;

    // Our own idempotency key sent to EcomWorldPay as "invoiceNumber" — generated at request
    // time so it's stable even if approval is retried.
    private String invoiceNumber;
    // EcomWorldPay's own id for this payout, returned as "ReferenceId" from the create call;
    // this is what we send back as "transactionId" on every subsequent status check.
    private String gatewayReferenceId;
    private String gatewayStatus;
    private String gatewayDescription;
    private String utrNumber;

    private String rejectionReason;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private LocalDateTime settledAt;
    private LocalDateTime updatedAt;
}
