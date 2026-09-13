package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.dto.AgentStatsDto;
import com.zivdah.chat.dto.AnalyticsSummaryResponseDto;
import com.zivdah.chat.dto.DailySeriesPointDto;
import com.zivdah.chat.repository.ChatAnalyticsRepository;
import com.zivdah.chat.service.AnalyticsService;
import com.zivdah.chat.service.SupportAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

// SQL aggregate queries throughout (via ChatAnalyticsRepository) — never a
// findAll()/collectList() reduced in the JVM, the exact class of bug already hit and fixed on
// payment-service's own /payments/stats endpoint (see the plan's analytics section).
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ChatAnalyticsRepository analyticsRepository;
    private final SupportAgentService supportAgentService;

    @Override
    public Mono<AnalyticsSummaryResponseDto> getSummary(LocalDateTime from, LocalDateTime to) {
        Mono<ChatAnalyticsRepository.CountsProjection> countsMono = analyticsRepository.counts(from, to);
        Mono<Double> firstResponseMono = analyticsRepository.avgFirstResponseTimeSeconds(from, to);
        Mono<Double> resolutionMono = analyticsRepository.avgResolutionTimeSeconds(from, to);
        Mono<Double> ratingMono = analyticsRepository.avgRating(from, to);
        Mono<List<DailySeriesPointDto>> seriesMono = analyticsRepository.dailySeries(from, to)
                .map(p -> DailySeriesPointDto.builder()
                        .date(p.day()).botCount(p.botCount()).humanCount(p.humanCount()).build())
                .collectList();

        return Mono.zip(countsMono, firstResponseMono, resolutionMono, ratingMono, seriesMono)
                .map(tuple -> {
                    ChatAnalyticsRepository.CountsProjection counts = tuple.getT1();
                    return AnalyticsSummaryResponseDto.builder()
                            .totalConversations(counts.total())
                            .botConversations(counts.botCount())
                            .humanConversations(counts.humanCount())
                            .botToHumanHandoffs(counts.handoffs())
                            .openCount(counts.openCount())
                            .waitingCount(counts.waitingCount())
                            .activeCount(counts.activeCount())
                            .closedCount(counts.closedCount())
                            .avgFirstResponseTimeSeconds(tuple.getT2())
                            .avgResolutionTimeSeconds(tuple.getT3())
                            .avgRating(tuple.getT4())
                            .series(tuple.getT5())
                            .build();
                });
    }

    @Override
    public Flux<AgentStatsDto> getAgentStats(LocalDateTime from, LocalDateTime to) {
        return analyticsRepository.perAgentConversationStats(from, to)
                .flatMap(row -> Mono.zip(
                                analyticsRepository.avgRatingForAgent(row.agentId()),
                                supportAgentService.resolveDisplayName(row.agentId()))
                        .map(names -> AgentStatsDto.builder()
                                .agentId(row.agentId())
                                .agentName(names.getT2())
                                .handled(row.handled())
                                .avgRating(names.getT1())
                                .avgResolutionTimeSeconds(row.avgResolutionSeconds())
                                .build()));
    }
}
