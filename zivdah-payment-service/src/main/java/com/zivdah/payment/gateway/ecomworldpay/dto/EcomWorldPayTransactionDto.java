package com.zivdah.payment.gateway.ecomworldpay.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Same shape for both EcomWorldPay's asynchronous Transaction Callback body and its Transaction
// Status API response — see PAYIN doc, API Name: TRANSACTION CALLBACK / TRANSACTION STATUS CHECK API.
// invoiceNumber is OUR transactionId (the invno we passed at QR creation); pgTxnId is the
// gateway's own id (Payment.gatewayTxnId).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EcomWorldPayTransactionDto {
    private String pgTxnId;
    private String status;
    private String responseCode;
    private String responseMessage;
    private String txnDate;
    private String sellerIdentifier;
    private String npciTxnId;
    private String rrn;
    private String invoiceNumber;
    private String gatewayResponseStatus;
    private BigDecimal amount;
    private String payerName;
    private String payerVPA;
    private String gatewayResponseMessage;
    private String paymentType;
    private String payerMobile;
}
