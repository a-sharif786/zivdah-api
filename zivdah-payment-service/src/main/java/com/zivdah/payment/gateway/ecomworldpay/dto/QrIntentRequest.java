package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Request body for EcomWorldPay's QR API (POST /api/registerupiintent) — field names/casing
// must match the gateway's contract exactly, see "PAYIN AND PAYOUT INTEGRATION" doc, API Name: QR API.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrIntentRequest {
    private String mid;
    private String amount;
    private String invno;
    private String firstName;
    private String lastName;
    private String mobile;
    private String currency;
    private String email;
}
