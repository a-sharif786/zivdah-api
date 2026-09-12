package com.zivdah.order.exception;

// Wraps any filesystem failure while writing/reading an invoice PDF (permissions, disk full,
// missing directory, etc.) so InvoiceServiceImpl can tell it apart from a business-rule error
// and report it distinctly (see requirement: "PDF generation failure" / "File storage failure").
public class InvoiceStorageException extends RuntimeException {
    public InvoiceStorageException(String message) {
        super(message);
    }

    public InvoiceStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
