package com.zivdah.logserver.service;

import com.zivdah.logserver.dto.LogEntryDto;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface LogQueryService {
    Flux<LogEntryDto> search(String serviceName, String level, OffsetDateTime from, OffsetDateTime to,
                              String messageContains, Pageable pageable);

    Mono<LogEntryDto> getById(Long id);
}
