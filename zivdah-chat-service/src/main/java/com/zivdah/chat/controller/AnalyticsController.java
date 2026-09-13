package com.zivdah.chat.controller;

import com.zivdah.chat.dto.AgentStatsDto;
import com.zivdah.chat.dto.AnalyticsSummaryResponseDto;
import com.zivdah.chat.dto.ApiResponse;
import com.zivdah.chat.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

// Section 19's support analytics.
@RestController
@RequestMapping("/restful/v1/api/chat/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public Mono<ResponseEntity<ApiResponse<AnalyticsSummaryResponseDto>>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return analyticsService.getSummary(from, to)
                .map(r -> ResponseEntity.ok(ApiResponse.<AnalyticsSummaryResponseDto>builder()
                        .status("success").statusCode(200).data(r).build()));
    }

    @GetMapping("/agents")
    public Mono<ResponseEntity<ApiResponse<List<AgentStatsDto>>>> getAgentStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return analyticsService.getAgentStats(from, to)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<AgentStatsDto>>builder()
                        .status("success").statusCode(200).data(list).build()));
    }
}
