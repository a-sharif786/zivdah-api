package com.zivdah.payment.dto;

import com.zivdah.payment.enums.PayoutMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Admin-submitted payout request body for a chosen vendor — unlike the vendor's own
// PayoutRequestDto, the admin explicitly picks the mode (UPI/IMPS/NEFT/RTGS) rather than
// having it auto-detected from what's on file; see VendorPayoutServiceImpl#initiateAdminPayout.
@Getter
@Setter
public class AdminPayoutRequestDto {
    @NotNull(message = "Vendor is required")
    private Long vendorId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payout mode is required")
    private PayoutMode payoutMode;
}
