package com.zivdah.common.event;

import lombok.*;

/**
 * Emitted by {@link com.zivdah.common.logging.KafkaLogAppender} for every log line captured
 * on a service's root logger, and consumed by zivdah-log-server off the "app-logs" Kafka
 * topic (see {@link com.zivdah.common.constants.KafkaTopics#APP_LOGS}) to persist a
 * centralized, queryable log history for the whole backend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvent {
    private String serviceName;
    private String level;
    private String loggerName;
    private String message;
    private String exception;
    private String correlationId;
    private String threadName;

    /**
     * ISO-8601 instant string (e.g. "2026-08-29T10:15:30.123Z"). Kept as a plain String
     * rather than java.time.Instant so the log-shipping appender — which runs before the
     * Spring ApplicationContext (and its JavaTimeModule-registered ObjectMapper) exists —
     * can serialize it with a bare, dependency-free Jackson ObjectMapper.
     */
    private String loggedAt;
}
