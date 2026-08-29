package com.zivdah.logserver.kafka;

import com.zivdah.common.constants.KafkaTopics;
import com.zivdah.common.event.LogEvent;
import com.zivdah.logserver.entity.LogEntry;
import com.zivdah.logserver.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Kafka listeners run on Kafka's blocking thread pool — .block() is safe here (same pattern
// as zivdah-order-service's PaymentCompletedConsumer).
@Service
@Slf4j
@RequiredArgsConstructor
public class LogEventConsumer {

    private final LogEntryRepository logEntryRepository;

    @KafkaListener(topics = KafkaTopics.APP_LOGS, groupId = "log-server-group")
    public void onLogEvent(LogEvent event) {
        LogEntry entry = LogEntry.builder()
                .serviceName(event.getServiceName())
                .logLevel(event.getLevel())
                .loggerName(event.getLoggerName())
                .message(event.getMessage())
                .exception(event.getException())
                .correlationId(event.getCorrelationId())
                .threadName(event.getThreadName())
                .loggedAt(parseLoggedAt(event.getLoggedAt()))
                .receivedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        logEntryRepository.save(entry).block();
    }

    private OffsetDateTime parseLoggedAt(String loggedAt) {
        if (loggedAt == null || loggedAt.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return Instant.parse(loggedAt).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Could not parse loggedAt '{}' on an incoming log event, using now() instead", loggedAt);
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
