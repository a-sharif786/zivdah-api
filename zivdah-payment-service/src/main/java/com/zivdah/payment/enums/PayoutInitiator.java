package com.zivdah.payment.enums;

// Who created a VendorPayout row — the vendor withdrawing their own earnings, or an admin
// initiating a payout on a vendor's behalf (see VendorPayoutServiceImpl#initiateAdminPayout).
public enum PayoutInitiator {
    VENDOR,
    ADMIN
}
