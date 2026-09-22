package com.zivdah.payment.enums;

// DTO/validation-layer only (see AdminPayoutRequestDto) — never persisted as this enum type.
// VendorPayout keeps its existing plain String payoutMode field, since submitPayout already
// forwards it mode-agnostically to EcomWorldPay.
public enum PayoutMode {
    UPI,
    IMPS,
    NEFT,
    RTGS
}
