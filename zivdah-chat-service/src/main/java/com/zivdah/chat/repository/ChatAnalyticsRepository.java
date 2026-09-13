package com.zivdah.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hand-written (not a Spring Data derived-query interface) — mirrors zivdah-payment-service's
 * PaymentStatsRepository exactly: these are SQL aggregate queries, summed/counted/averaged in
 * Postgres rather than loading every conversation/rating row into memory and reducing it in the
 * JVM (the same class of bug already hit and fixed on payment-service's own /payments/stats).
 */
@Repository
@RequiredArgsConstructor
public class ChatAnalyticsRepository {

    private final DatabaseClient databaseClient;

    public Mono<CountsProjection> counts(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT COUNT(*) AS total, " +
                        "COUNT(*) FILTER (WHERE type = 'BOT') AS bot_count, " +
                        "COUNT(*) FILTER (WHERE type = 'HUMAN') AS human_count, " +
                        "COUNT(*) FILTER (WHERE handed_off_at IS NOT NULL) AS handoffs, " +
                        "COUNT(*) FILTER (WHERE status = 'OPEN') AS open_count, " +
                        "COUNT(*) FILTER (WHERE status = 'WAITING') AS waiting_count, " +
                        "COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_count, " +
                        "COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_count " +
                        "FROM conversations WHERE created_at BETWEEN :from AND :to")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new CountsProjection(
                        row.get("total", Long.class), row.get("bot_count", Long.class),
                        row.get("human_count", Long.class), row.get("handoffs", Long.class),
                        row.get("open_count", Long.class), row.get("waiting_count", Long.class),
                        row.get("active_count", Long.class), row.get("closed_count", Long.class)))
                .one();
    }

    // Approximate "time to first response": handed_off_at -> updated_at over conversations still
    // ACTIVE (updated_at was last touched by the accept, so it's a reasonable proxy) — there's no
    // dedicated acceptedAt/firstResponseAt column to compute this precisely yet.
    public Mono<Double> avgFirstResponseTimeSeconds(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT AVG(EXTRACT(EPOCH FROM (updated_at - handed_off_at))) AS avg_seconds " +
                        "FROM conversations WHERE status = 'ACTIVE' AND handed_off_at IS NOT NULL " +
                        "AND created_at BETWEEN :from AND :to")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> row.get("avg_seconds", Double.class))
                .one()
                .defaultIfEmpty(0.0);
    }

    public Mono<Double> avgResolutionTimeSeconds(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT AVG(EXTRACT(EPOCH FROM (closed_at - handed_off_at))) AS avg_seconds " +
                        "FROM conversations WHERE status = 'CLOSED' AND handed_off_at IS NOT NULL " +
                        "AND closed_at IS NOT NULL AND created_at BETWEEN :from AND :to")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> row.get("avg_seconds", Double.class))
                .one()
                .defaultIfEmpty(0.0);
    }

    public Mono<Double> avgRating(LocalDateTime from, LocalDateTime to) {
        // rating is smallint — Postgres's AVG() of an integer type returns numeric, not double
        // precision, so the explicit cast keeps the R2DBC row mapping to Double.class working.
        return databaseClient.sql(
                        "SELECT CAST(AVG(rating) AS double precision) AS avg_rating FROM conversation_ratings " +
                        "WHERE created_at BETWEEN :from AND :to")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> row.get("avg_rating", Double.class))
                .one()
                .defaultIfEmpty(0.0);
    }

    public Flux<DailyCountProjection> dailySeries(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT CAST(created_at AS date) AS day, " +
                        "COUNT(*) FILTER (WHERE type = 'BOT') AS bot_count, " +
                        "COUNT(*) FILTER (WHERE type = 'HUMAN') AS human_count " +
                        "FROM conversations WHERE created_at BETWEEN :from AND :to " +
                        "GROUP BY CAST(created_at AS date) ORDER BY day")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new DailyCountProjection(
                        row.get("day", LocalDate.class), row.get("bot_count", Long.class), row.get("human_count", Long.class)))
                .all();
    }

    public Flux<AgentConversationStatsProjection> perAgentConversationStats(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql(
                        "SELECT assigned_agent_id AS agent_id, " +
                        "COUNT(*) FILTER (WHERE status = 'CLOSED') AS handled, " +
                        "AVG(EXTRACT(EPOCH FROM (closed_at - handed_off_at))) FILTER (WHERE status = 'CLOSED') AS avg_resolution_seconds " +
                        "FROM conversations WHERE assigned_agent_id IS NOT NULL AND created_at BETWEEN :from AND :to " +
                        "GROUP BY assigned_agent_id")
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new AgentConversationStatsProjection(
                        row.get("agent_id", Long.class), row.get("handled", Long.class),
                        row.get("avg_resolution_seconds", Double.class)))
                .all();
    }

    public Mono<Double> avgRatingForAgent(Long agentId) {
        return databaseClient.sql("SELECT AVG(rating) AS avg_rating FROM conversation_ratings WHERE agent_id = :agentId")
                .bind("agentId", agentId)
                .map((row, meta) -> row.get("avg_rating", Double.class))
                .one()
                .defaultIfEmpty(0.0);
    }

    public record CountsProjection(long total, long botCount, long humanCount, long handoffs,
                                    long openCount, long waitingCount, long activeCount, long closedCount) {
    }

    public record DailyCountProjection(LocalDate day, long botCount, long humanCount) {
    }

    public record AgentConversationStatsProjection(Long agentId, long handled, Double avgResolutionSeconds) {
    }
}
