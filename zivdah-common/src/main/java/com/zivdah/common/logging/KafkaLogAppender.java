package com.zivdah.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.common.event.LogEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class KafkaLogAppender extends AppenderBase<ILoggingEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String bootstrapServers;
    private String topic;
    private final AtomicReference<KafkaProducer<String, String>> producerRef = new AtomicReference<>();

    // Logback/Joran setters, bound from the <bootstrapServers>/<topic> child elements of the
    // <appender> declaration in kafka-appender-include.xml.
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Override
    protected void append(ILoggingEvent event) {
        String serviceName = context.getProperty("APP_NAME");
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = "unknown-service";
        }
        try {
            LogEvent payload = LogEvent.builder()
                    .serviceName(serviceName)
                    .level(event.getLevel().toString())
                    .loggerName(event.getLoggerName())
                    // Scrubbed before it leaves the service: the log store is long-lived and
                    // searchable, so no token/password/OTP/e-mail/mobile should ever land in it
                    // verbatim, whatever an individual log statement happened to include.
                    .message(LogSanitizer.sanitize(event.getFormattedMessage()))
                    .exception(event.getThrowableProxy() != null
                            ? LogSanitizer.sanitize(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                            : null)
                    .correlationId(event.getMDCPropertyMap() != null
                            ? event.getMDCPropertyMap().get("correlationId")
                            : null)
                    .threadName(event.getThreadName())
                    .loggedAt(Instant.ofEpochMilli(event.getTimeStamp()).toString())
                    .build();
            String json = MAPPER.writeValueAsString(payload);
            producer().send(new ProducerRecord<>(topic, serviceName, json));
        } catch (Exception e) {
            // Never let log shipping break the application, and never log through this same
            // appender again (would recurse) — addError just reports to Logback's own
            // status manager.
            addError("Failed to publish log event to Kafka topic '" + topic + "'", e);
        }
    }

    private KafkaProducer<String, String> producer() {
        KafkaProducer<String, String> existing = producerRef.get();
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            existing = producerRef.get();
            if (existing != null) {
                return existing;
            }
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            // Fire-and-forget: shipping logs must never add request-path latency or backpressure.
            props.put(ProducerConfig.ACKS_CONFIG, "0");
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
            KafkaProducer<String, String> created = new KafkaProducer<>(props);
            producerRef.set(created);
            return created;
        }
    }

    @Override
    public void stop() {
        KafkaProducer<String, String> existing = producerRef.getAndSet(null);
        if (existing != null) {
            existing.close(Duration.ofMillis(500));
        }
        super.stop();
    }
}
