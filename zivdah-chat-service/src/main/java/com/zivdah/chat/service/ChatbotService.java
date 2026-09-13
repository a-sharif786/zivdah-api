package com.zivdah.chat.service;

import com.zivdah.chat.dto.BotConfirmRequestDto;
import com.zivdah.chat.dto.BotMessageRequestDto;
import com.zivdah.chat.dto.BotMessageResponseDto;
import reactor.core.publisher.Mono;

public interface ChatbotService {

    // One turn of the bot conversation flow: create-or-load conversation (ownership-checked),
    // persist the inbound message, skip the bot if the conversation is already HUMAN-type,
    // otherwise classify + reply via ChatbotProvider, persist the bot reply, and transition to
    // human handoff if the reply asked for one. bearerToken is the caller's own validated JWT,
    // forwarded to ChatContext for the handful of downstream service calls that need it.
    Mono<BotMessageResponseDto> handleBotMessage(Long customerId, String bearerToken, BotMessageRequestDto request);

    // Resolves a requiresConfirmation prompt (currently only CANCEL_ORDER exists). Re-validates
    // ownership and cancellable-state itself — never trusts the client-echoed payload blindly.
    Mono<BotMessageResponseDto> confirmAction(Long customerId, String bearerToken, BotConfirmRequestDto request);
}
