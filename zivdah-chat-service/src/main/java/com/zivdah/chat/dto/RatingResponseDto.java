package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingResponseDto {
    private Long conversationId;
    private Long customerId;
    private Long agentId;
    private Short rating;
    private String feedback;
    private LocalDateTime createdAt;
}
