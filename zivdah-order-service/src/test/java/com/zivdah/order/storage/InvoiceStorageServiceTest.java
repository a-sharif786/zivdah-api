package com.zivdah.order.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceStorageServiceTest {

    private InvoiceStorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new InvoiceStorageService();
        ReflectionTestUtils.setField(storageService, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(storageService, "publicBaseUrl", "https://zivdahonlinegrocery.com/media/invoices");
    }

    @Test
    void save_writesFileUnderStorageDirectory_andReturnsThePublicUrl() {
        byte[] bytes = "not-a-real-pdf".getBytes();

        StepVerifier.create(storageService.save("INV-2026-000001", bytes))
                .expectNextMatches(stored -> stored.publicUrl()
                        .equals("https://zivdahonlinegrocery.com/media/invoices/INV-2026-000001.pdf"))
                .verifyComplete();

        assertTrue(Files.exists(tempDir.resolve("INV-2026-000001.pdf")));
    }

    @Test
    void save_rejectsPathTraversalAttempts_inTheInvoiceNumber() {
        StepVerifier.create(storageService.save("../../etc/passwd", "x".getBytes()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void save_rejectsInvoiceNumbersWithPathSeparators() {
        StepVerifier.create(storageService.save("INV/2026/000001", "x".getBytes()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void read_returns404_whenTheFileDoesNotExistOnDisk() {
        StepVerifier.create(storageService.read("INV-2026-999999"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException
                        && ((ResponseStatusException) ex).getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void readsBackWhatWasJustSaved() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        storageService.save("INV-2026-000002", bytes).block();

        StepVerifier.create(storageService.read("INV-2026-000002"))
                .expectNextMatches(read -> read.length == 4)
                .verifyComplete();
    }
}
