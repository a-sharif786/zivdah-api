package com.zivdah.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotMessageRequestDto {

    // Null starts a new BOT conversation for the caller; otherwise must belong to the caller
    // (enforced server-side, never trusted as-is).
    private Long conversationId;

    @NotBlank
    @Size(max = 4000)
    private String message;
}
