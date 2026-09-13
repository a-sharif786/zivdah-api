package com.zivdah.chat.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryResponseDto {
    private long totalConversations;
    private long botConversations;
    private long humanConversations;
    private long botToHumanHandoffs;
    private long openCount;
    private long waitingCount;
    private long activeCount;
    private long closedCount;
    // Approximate: (handed_off_at -> updated_at) averaged over currently-ACTIVE conversations
    // only, as a proxy for "time to first agent response" — there's no dedicated acceptedAt/
    // firstResponseAt timestamp to compute this precisely yet (see ChatAnalyticsRepository).
    private Double avgFirstResponseTimeSeconds;
    // (handed_off_at -> closed_at) averaged over CLOSED HUMAN conversations.
    private Double avgResolutionTimeSeconds;
    private Double avgRating;
    private List<DailySeriesPointDto> series;
}
