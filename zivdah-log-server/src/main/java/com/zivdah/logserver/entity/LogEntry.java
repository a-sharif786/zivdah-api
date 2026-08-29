package com.zivdah.logserver.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntry {

    @Id
    private Long id;

    private String serviceName;
    private String logLevel;
    private String loggerName;
    private String message;
    private String exception;
    private String correlationId;
    private String threadName;

    private OffsetDateTime loggedAt;
    private OffsetDateTime receivedAt;
}
