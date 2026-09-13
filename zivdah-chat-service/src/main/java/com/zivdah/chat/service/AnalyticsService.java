package com.zivdah.chat.service;

import com.zivdah.chat.dto.AgentStatsDto;
import com.zivdah.chat.dto.AnalyticsSummaryResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface AnalyticsService {

    Mono<AnalyticsSummaryResponseDto> getSummary(LocalDateTime from, LocalDateTime to);

    Flux<AgentStatsDto> getAgentStats(LocalDateTime from, LocalDateTime to);
}
