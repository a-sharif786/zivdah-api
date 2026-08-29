package com.zivdah.logserver.serviceImpl;

import com.zivdah.logserver.dto.LogEntryDto;
import com.zivdah.logserver.entity.LogEntry;
import com.zivdah.logserver.exception.ResourceNotFoundException;
import com.zivdah.logserver.repository.LogEntryRepository;
import com.zivdah.logserver.repository.LogQueryRepository;
import com.zivdah.logserver.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    private final LogQueryRepository logQueryRepository;
    private final LogEntryRepository logEntryRepository;

    @Override
    public Flux<LogEntryDto> search(String serviceName, String level, OffsetDateTime from, OffsetDateTime to,
                                     String messageContains, Pageable pageable) {
        return logQueryRepository.search(serviceName, level, from, to, messageContains, pageable)
                .map(this::toDto);
    }

    @Override
    public Mono<LogEntryDto> getById(Long id) {
        return logEntryRepository.findById(id)
                .map(this::toDto)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Log entry not found: " + id)));
    }

    private LogEntryDto toDto(LogEntry entry) {
        return LogEntryDto.builder()
                .id(entry.getId())
                .serviceName(entry.getServiceName())
                .logLevel(entry.getLogLevel())
                .loggerName(entry.getLoggerName())
                .message(entry.getMessage())
                .exception(entry.getException())
                .correlationId(entry.getCorrelationId())
                .threadName(entry.getThreadName())
                .loggedAt(entry.getLoggedAt())
                .receivedAt(entry.getReceivedAt())
                .build();
    }
}
