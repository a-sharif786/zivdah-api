package com.zivdah.chat.dto;

import lombok.*;

// Resolved by the client via POST /chat/bot/confirm, echoing actionType/payload back verbatim
// (see BotConfirmRequestDto) — the server never re-parses free text to figure out what the
// customer meant, and never trusts the echoed payload blindly either (see
// ChatbotServiceImpl#confirmAction, which always re-fetches and re-validates before acting).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmationPromptDto {

    // e.g. "CANCEL_ORDER" — the only actionType that exists today.
    private String actionType;

    // e.g. {"orderId": 1001} — opaque to the frontend, echoed back verbatim in
    // BotConfirmRequestDto#payload.
    private Object payload;

    // e.g. "Are you sure you want to cancel Order #ZVD1001?"
    private String prompt;
}
