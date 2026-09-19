package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

// Payout Payment Status API request body (POST /api/payout/transStatus) — transactionId is
// the "ReferenceId" EcomWorldPay returned from the original payout-create call.
@Getter
@Setter
@AllArgsConstructor
@Builder
public class EcomWorldPayPayoutStatusRequest {
    private String transactionId;
}
