package com.zivdah.order.storage;

import com.zivdah.order.exception.InvoiceStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

// Reads/writes invoice PDFs on the local filesystem under invoice.storage.path (prod:
// /var/www/zivdah-media/invoices/, served by nginx directly under /media/invoices/ — see
// nginx/invoices-media.conf; dev: a project-relative folder, since /var/www doesn't exist on a
// Windows dev box). Only the resulting *URL* is ever persisted (Invoice.pdfUrl) — never the raw
// bytes, never the filesystem path — see the requirement not to store PDF binaries in Postgres.
@Slf4j
@Component
public class InvoiceStorageService {

    // Invoice numbers are always server-generated (INV-YYYY-NNNNNN, see InvoiceNumberGenerator)
    // and never come from user input, but every path built from one is still validated against
    // this allowlist and re-checked to resolve inside storageDir — belt-and-suspenders against
    // path traversal, per the invoice security requirement.
    private static final Pattern SAFE_INVOICE_NUMBER = Pattern.compile("^[A-Za-z0-9\\-]{1,64}$");

    @Value("${invoice.storage.path}")
    private String storagePath;

    @Value("${invoice.public-base-url}")
    private String publicBaseUrl;

    public record StoredInvoiceFile(String publicUrl) {}

    public Mono<StoredInvoiceFile> save(String invoiceNumber, byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
                    Path target = resolveTarget(invoiceNumber);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.write(target, pdfBytes);
                    } catch (IOException e) {
                        throw new InvoiceStorageException("Failed to store invoice PDF: " + e.getMessage(), e);
                    }
                    log.info("Invoice PDF stored: {}", target);
                    return new StoredInvoiceFile(publicUrl(invoiceNumber));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<byte[]> read(String invoiceNumber) {
        return Mono.fromCallable(() -> {
                    Path target = resolveTarget(invoiceNumber);
                    if (!Files.exists(target)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Invoice PDF file not found on disk: " + invoiceNumber);
                    }
                    try {
                        return Files.readAllBytes(target);
                    } catch (IOException e) {
                        throw new InvoiceStorageException("Failed to read invoice PDF: " + e.getMessage(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Path resolveTarget(String invoiceNumber) {
        if (invoiceNumber == null || !SAFE_INVOICE_NUMBER.matcher(invoiceNumber).matches()) {
            throw new IllegalArgumentException("Invalid invoice number: " + invoiceNumber);
        }
        Path baseDir = Paths.get(storagePath).toAbsolutePath().normalize();
        Path target = baseDir.resolve(invoiceNumber + ".pdf").normalize();
        if (!target.startsWith(baseDir)) {
            // Can't actually happen given the allowlist above, but this is the real
            // traversal guard — kept regardless of how invoiceNumber ends up produced later.
            throw new InvoiceStorageException("Resolved invoice path escapes the configured storage directory");
        }
        return target;
    }

    private String publicUrl(String invoiceNumber) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/" + invoiceNumber + ".pdf";
    }
}
