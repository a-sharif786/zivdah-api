package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

// Payout Payment API request body (POST /api/payout/payments) — field names/casing match the
// gateway's docs exactly. accountNo/ifscBankCode are used for payoutMode "IMPS"; payeeVpa is
// used for payoutMode "UPI" (the other left blank, per the gateway's own sample packets).
@Getter
@Setter
@AllArgsConstructor
@Builder
public class EcomWorldPayPayoutRequest {
    private String invoiceNumber;
    private String merchantId;
    private String customerName;
    private String phoneNumber;
    private String payoutMode;
    private String payoutAmount;
    private String accountNo;
    private String ifscBankCode;
    private String secretKey;
    private String apiKey;
    private String ipAddress;
    private String payeeVpa;
}
