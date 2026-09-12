package com.zivdah.order.exception;

// Wraps any failure while rendering the invoice PDF itself (as opposed to
// InvoiceStorageException, which covers writing/reading the rendered bytes to disk).
public class InvoicePdfGenerationException extends RuntimeException {
    public InvoicePdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
