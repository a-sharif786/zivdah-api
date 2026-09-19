package com.zivdah.payment.gateway.ecomworldpay;

// Thrown when EcomWorldPay's Transaction Status API responds with its "not resolvable yet" envelope
// ({"Data":{"Status":"Failed","Status code":"400","Description":"intent not generated / transaction
// not initiated."}}) instead of the normal flat EcomWorldPayTransactionDto shape. This happens while
// the customer's UPI payment is still in flight (scanned but not yet settled by the bank/NPCI) — it is
// NOT a declined payment, and must not be treated as one (see PaymentServiceImpl#applyGatewayResult).
public class TransactionNotYetAvailableException extends RuntimeException {
    public TransactionNotYetAvailableException(String gatewayDescription) {
        super(gatewayDescription);
    }
}
