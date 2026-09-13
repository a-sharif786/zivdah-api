package com.zivdah.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("support_agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportAgent {

    @Id
    private Long id;

    private Long userId;

    private String displayName;

    private Boolean active;

    private Integer maxConcurrentChats;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
