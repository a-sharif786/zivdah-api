package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Request body for EcomWorldPay's Transaction Status API (POST /api/merchant/transactions/status).
// transactionId here is the GATEWAY's transaction id (Payment.gatewayTxnId), not our own.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusRequest {
    private String transactionId;
    private String merchantId;
    private String secretKey;
    private String apiKey;
}
