package com.zivdah.payment.gateway.ecomworldpay;

// Carries EcomWorldPay's own error text — extracted from a non-2xx response body, a 200 OK
// response that wasn't the documented JSON shape (e.g. a bare "Your IP is not whitelisted."), or
// the "message" field of a decoded FAIL response — so callers can surface it to the customer
// verbatim instead of a generic failure message.
public class EcomWorldPayException extends RuntimeException {
    public EcomWorldPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
