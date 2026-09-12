package com.zivdah.payment.repository;

import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Hand-written (not a Spring Data @Query interface projection) — see PaymentTotalsProjection.
// These three aggregate queries don't map onto the Payment entity's own columns, and interface
// projections built off them were observed returning null gross/refunded/amount through the
// R2DBC row->proxy mapping despite the identical SQL returning correct non-null values when run
// directly against Postgres. Reading columns straight off the Row via DatabaseClient here
// sidesteps that mapping layer entirely (same pattern as zivdah-log-server's LogQueryRepository).
@Repository
@RequiredArgsConstructor
public class PaymentStatsRepository {

    private final DatabaseClient databaseClient;

    private static PaymentTotalsProjection readTotals(Row row) {
        return new PaymentTotalsProjection(row.get("gross", BigDecimal.class), row.get("refunded", BigDecimal.class));
    }

    // Stats support (see PaymentServiceImpl#getStats). Summed in SQL rather than loading every
    // SUCCESS/REFUNDED payment ever into memory — this used to be a findByStatusIn(...).collectList()
    // here, which was instant against a small dev DB but got slow/heavy in production once real
    // payment volume accumulated (every dashboard load re-ran it), eventually manifesting as a
    // gateway 503 for this endpoint only. SUCCESS + REFUNDED together = "ever successfully paid",
    // so refund amounts are netted out below rather than the payment vanishing from the sum.
    public Mono<PaymentTotalsProjection> sumTotalsAllTime() {
        return databaseClient.sql(
                        "SELECT COALESCE(SUM(amount), 0) AS gross, COALESCE(SUM(refund_amount), 0) AS refunded " +
                        "FROM payments WHERE status IN ('SUCCESS', 'REFUNDED')")
                .map((row, meta) -> readTotals(row))
                .one();
    }

    public Mono<PaymentTotalsProjection> sumTotalsInRange(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT COALESCE(SUM(amount), 0) AS gross, COALESCE(SUM(refund_amount), 0) AS refunded " +
                        "FROM payments WHERE status IN ('SUCCESS', 'REFUNDED') AND paid_at BETWEEN :from AND :to")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> readTotals(row))
                .one();
    }

    // Net (amount - refundAmount) bucketed by calendar day of paidAt, ascending — feeds the
    // dashboard trend chart directly, without pulling individual rows into the JVM to bucket them.
    public Flux<DailyNetProjection> dailyNetSeries(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT CAST(paid_at AS date) AS day, COALESCE(SUM(amount - COALESCE(refund_amount, 0)), 0) AS amount " +
                        "FROM payments WHERE status IN ('SUCCESS', 'REFUNDED') AND paid_at BETWEEN :from AND :to " +
                        "GROUP BY CAST(paid_at AS date) ORDER BY day")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new DailyNetProjection(row.get("day", LocalDate.class), row.get("amount", BigDecimal.class)))
                .all();
    }
}
