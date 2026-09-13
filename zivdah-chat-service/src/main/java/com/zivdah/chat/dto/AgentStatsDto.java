package com.zivdah.chat.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStatsDto {
    private Long agentId;
    private String agentName;
    private long handled;
    private Double avgRating;
    private Double avgResolutionTimeSeconds;
}
