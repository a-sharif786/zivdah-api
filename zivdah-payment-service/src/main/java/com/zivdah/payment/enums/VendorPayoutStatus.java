package com.zivdah.payment.enums;

public enum VendorPayoutStatus {
    // Vendor submitted the request; awaiting admin review.
    REQUESTED,
    // Admin approved and EcomWorldPay accepted the payout for processing.
    PROCESSING,
    // EcomWorldPay confirmed the settlement completed.
    SUCCESS,
    // EcomWorldPay declined/rejected the payout, or its request-time validation failed.
    FAILED,
    // Admin declined the request before any gateway call was made.
    REJECTED
}
