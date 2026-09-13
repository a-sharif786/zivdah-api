package com.zivdah.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// userId must already be an ADMIN account (chat-service has no visibility into auth-service's
// role assignments to verify this itself — see the role decision in the plan: support agents are
// ADMIN users flagged in this roster, not a distinct role).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportAgentRequestDto {

    @NotNull
    private Long userId;

    @NotBlank
    private String displayName;

    private Integer maxConcurrentChats;
}
