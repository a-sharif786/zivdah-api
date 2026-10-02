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
    private String loggedAt;
}
