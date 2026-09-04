package com.zivdah.logserver.repository;

import com.zivdah.logserver.entity.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

import static org.springframework.data.relational.core.query.Criteria.where;

/**
 * Hand-written (not a Spring Data derived-query interface) because the log search endpoint
 * combines several independently-optional filters — a plain {@code ReactiveCrudRepository}
 * method name can't express "any subset of these five filters, whichever are present".
 * {@link Criteria#empty()} composes cleanly with {@code .and(...)}, so only the filters the
 * caller actually supplied end up in the generated WHERE clause.
 */
@Repository
@RequiredArgsConstructor
public class LogQueryRepository {

    private final R2dbcEntityTemplate template;

    public Flux<LogEntry> search(String serviceName, String level, OffsetDateTime from, OffsetDateTime to,
                                  String messageContains, Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (serviceName != null && !serviceName.isBlank()) {
            criteria = criteria.and(where("service_name").is(serviceName));
        }
        if (level != null && !level.isBlank()) {
            criteria = criteria.and(where("log_level").is(level.toUpperCase()));
        }
        if (from != null) {
            criteria = criteria.and(where("logged_at").greaterThanOrEquals(from));
        }
        if (to != null) {
            criteria = criteria.and(where("logged_at").lessThanOrEquals(to));
        }
        if (messageContains != null && !messageContains.isBlank()) {
            criteria = criteria.and(where("message").like("%" + messageContains + "%"));
        }

        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Direction.DESC, "logged_at"))
                .with(pageable);

        return template.select(query, LogEntry.class);
    }
}
