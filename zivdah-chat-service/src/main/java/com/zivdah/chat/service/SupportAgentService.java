package com.zivdah.chat.service;

import com.zivdah.chat.dto.SupportAgentRequestDto;
import com.zivdah.chat.dto.SupportAgentResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SupportAgentService {

    Flux<SupportAgentResponseDto> getAgents();

    // DuplicateResourceException (409) if userId is already on the roster.
    Mono<SupportAgentResponseDto> addAgent(SupportAgentRequestDto request);

    // ResourceNotFoundException (404) if userId isn't on the roster.
    Mono<SupportAgentResponseDto> toggleActive(Long userId);

    // Used by ConversationServiceImpl to gate accept/close: true only if userId is on the
    // roster AND active — a bare ADMIN role is not enough (see the plan's security section).
    Mono<Boolean> isActiveAgent(Long userId);

    // Falls back to a generic label if the caller isn't (or is no longer) on the roster — never
    // fails the accept/close flow itself just because a display name couldn't be resolved.
    Mono<String> resolveDisplayName(Long userId);
}
