package com.zivdah.chat.repository;

import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.enums.ConversationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import static org.springframework.data.relational.core.query.Criteria.where;

/**
 * Hand-written (not a Spring Data derived-query interface) — mirrors zivdah-log-server's
 * LogQueryRepository exactly: {@code GET /conversations/search} combines several independently-
 * optional filters that a plain derived-query method name can't express. {@link Criteria#empty()}
 * composes cleanly with {@code .and(...)}, so only the filters the caller actually supplied end
 * up in the generated WHERE clause.
 *
 * <p>Note: a free-text {@code q} search over message content is NOT implemented here —
 * {@code messages} is a separate aggregate root from {@code conversations}, so a text match
 * there can't be expressed as a filter on this entity alone; this endpoint covers the structured
 * filters (customer/order/status/agent/date-range) only.
 */
@Repository
@RequiredArgsConstructor
public class ConversationSearchRepository {

    private final R2dbcEntityTemplate template;

    public Flux<ChatConversation> search(Long customerId, Long orderId, ConversationStatus status, Long agentId,
                                          LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (customerId != null) {
            criteria = criteria.and(where("customer_id").is(customerId));
        }
        if (orderId != null) {
            criteria = criteria.and(where("order_id").is(orderId));
        }
        if (status != null) {
            criteria = criteria.and(where("status").is(status.name()));
        }
        if (agentId != null) {
            criteria = criteria.and(where("assigned_agent_id").is(agentId));
        }
        if (from != null) {
            criteria = criteria.and(where("created_at").greaterThanOrEquals(from));
        }
        if (to != null) {
            criteria = criteria.and(where("created_at").lessThanOrEquals(to));
        }

        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Direction.DESC, "updated_at"))
                .with(pageable);

        return template.select(query, ChatConversation.class);
    }
}
