package com.zivdah.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotConfirmRequestDto {

    @NotNull
    private Long conversationId;

    // Echoed back verbatim from the requiresConfirmation prompt the bot gave the client, e.g.
    // "CANCEL_ORDER" — see BotMessageResponseDto#requiresConfirmation (Phase 4).
    @NotBlank
    private String actionType;

    // Echoed back verbatim from the requiresConfirmation prompt's payload — never re-parsed from
    // free text.
    private Object payload;

    private boolean confirmed;
}
