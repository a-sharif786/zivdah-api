package com.zivdah.order.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Year;

// Hand-written against DatabaseClient rather than a derived repository query — same reasoning
// as PaymentServiceImpl's PaymentStatsRepository: this needs a single atomic
// upsert-and-increment statement, which a plain @Query projection can't express. The upsert
// (INSERT ... ON CONFLICT ... RETURNING) is what makes concurrent invoice generation
// race-safe: two requests in the same year both get a strictly increasing, never-repeated
// last_seq straight from Postgres, with no read-then-write gap in application code.
@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final DatabaseClient databaseClient;

    public Mono<String> nextInvoiceNumber() {
        int year = Year.now().getValue();
        return databaseClient.sql("""
                        INSERT INTO invoice_number_counters (year, last_seq) VALUES (:year, 1)
                        ON CONFLICT (year) DO UPDATE SET last_seq = invoice_number_counters.last_seq + 1
                        RETURNING last_seq
                        """)
                .bind("year", year)
                .map(row -> row.get("last_seq", Long.class))
                .one()
                .map(seq -> "INV-%d-%06d".formatted(year, seq));
    }
}
