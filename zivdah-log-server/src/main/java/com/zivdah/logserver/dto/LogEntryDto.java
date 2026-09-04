package com.zivdah.logserver.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntryDto {
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
