package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.dto.SupportAgentRequestDto;
import com.zivdah.chat.dto.SupportAgentResponseDto;
import com.zivdah.chat.entity.SupportAgent;
import com.zivdah.chat.exception.DuplicateResourceException;
import com.zivdah.chat.exception.ResourceNotFoundException;
import com.zivdah.chat.repository.SupportAgentRepository;
import com.zivdah.chat.service.SupportAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SupportAgentServiceImpl implements SupportAgentService {

    private static final String DEFAULT_DISPLAY_NAME = "Support Agent";
    private static final int DEFAULT_MAX_CONCURRENT_CHATS = 5;

    private final SupportAgentRepository supportAgentRepository;

    @Override
    public Flux<SupportAgentResponseDto> getAgents() {
        return supportAgentRepository.findAll().map(this::toDto);
    }

    @Override
    public Mono<SupportAgentResponseDto> addAgent(SupportAgentRequestDto request) {
        return supportAgentRepository.findByUserId(request.getUserId())
                .flatMap(existing -> Mono.<SupportAgentResponseDto>error(new DuplicateResourceException(
                        "User " + request.getUserId() + " is already a support agent")))
                .switchIfEmpty(Mono.defer(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    SupportAgent agent = SupportAgent.builder()
                            .userId(request.getUserId())
                            .displayName(request.getDisplayName())
                            .active(true)
                            .maxConcurrentChats(request.getMaxConcurrentChats() != null
                                    ? request.getMaxConcurrentChats() : DEFAULT_MAX_CONCURRENT_CHATS)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return supportAgentRepository.save(agent).map(this::toDto);
                }));
    }

    @Override
    public Mono<SupportAgentResponseDto> toggleActive(Long userId) {
        return supportAgentRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Support agent not found: " + userId)))
                .flatMap(agent -> {
                    agent.setActive(!Boolean.TRUE.equals(agent.getActive()));
                    agent.setUpdatedAt(LocalDateTime.now());
                    return supportAgentRepository.save(agent);
                })
                .map(this::toDto);
    }

    @Override
    public Mono<Boolean> isActiveAgent(Long userId) {
        return supportAgentRepository.findByUserId(userId)
                .map(agent -> Boolean.TRUE.equals(agent.getActive()))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<String> resolveDisplayName(Long userId) {
        return supportAgentRepository.findByUserId(userId)
                .map(SupportAgent::getDisplayName)
                .defaultIfEmpty(DEFAULT_DISPLAY_NAME);
    }

    private SupportAgentResponseDto toDto(SupportAgent agent) {
        return SupportAgentResponseDto.builder()
                .id(agent.getId())
                .userId(agent.getUserId())
                .displayName(agent.getDisplayName())
                .active(agent.getActive())
                .maxConcurrentChats(agent.getMaxConcurrentChats())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}
