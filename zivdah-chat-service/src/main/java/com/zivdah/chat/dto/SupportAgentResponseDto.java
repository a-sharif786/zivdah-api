package com.zivdah.chat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportAgentResponseDto {
    private Long id;
    private Long userId;
    private String displayName;
    private Boolean active;
    private Integer maxConcurrentChats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
