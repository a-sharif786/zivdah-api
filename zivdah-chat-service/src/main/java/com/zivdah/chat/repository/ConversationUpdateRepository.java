package com.zivdah.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

// Hand-written against DatabaseClient — same reasoning as PaymentStatsRepository/
// InvoiceNumberGenerator elsewhere in the platform. The WAITING -> ACTIVE transition needs a
// single atomic conditional UPDATE, not a findById-then-save round trip: two agents racing to
// accept the same WAITING conversation would otherwise both read status=WAITING, both mutate
// their own in-memory copy, and the second save() would silently clobber the first agent's claim.
// Gating the UPDATE itself on "AND status = 'WAITING'" and checking rows-affected closes that gap
// at the database, not in application code.
@Repository
@RequiredArgsConstructor
public class ConversationUpdateRepository {

    private final DatabaseClient databaseClient;

    // Returns the number of rows updated: 1 = this caller won the race and is now the assigned
    // agent; 0 = someone else claimed it (or it was no longer WAITING) first.
    public Mono<Long> acceptIfWaiting(Long conversationId, Long agentId) {
        return databaseClient.sql(
                        "UPDATE conversations SET status = 'ACTIVE', assigned_agent_id = :agentId, " +
                        "updated_at = now() WHERE id = :id AND status = 'WAITING'")
                .bind("agentId", agentId)
                .bind("id", conversationId)
                .fetch()
                .rowsUpdated();
    }
}
