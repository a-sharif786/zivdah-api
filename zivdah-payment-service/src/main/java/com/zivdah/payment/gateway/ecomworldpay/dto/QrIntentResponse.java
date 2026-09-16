package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Response body from EcomWorldPay's QR API. "status" here is the intent-creation outcome
// (SUCCESS/FAILED), NOT the payment outcome — the actual payment result arrives later via the
// transaction callback / status-check API as an EcomWorldPayTransactionDto.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QrIntentResponse {
    private String merchantIdentifier;
    private String amount;
    private String orderId;
    private String currency;
    private String transactionDate;
    private String intent;
    private String transactionId;
    private String status;
}
