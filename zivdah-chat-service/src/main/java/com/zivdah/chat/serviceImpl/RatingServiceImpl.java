package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.dto.RatingRequestDto;
import com.zivdah.chat.dto.RatingResponseDto;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ConversationRating;
import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.exception.DuplicateResourceException;
import com.zivdah.chat.exception.ForbiddenOperationException;
import com.zivdah.chat.repository.ConversationRatingRepository;
import com.zivdah.chat.service.ConversationService;
import com.zivdah.chat.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final ConversationService conversationService;
    private final ConversationRatingRepository conversationRatingRepository;

    @Override
    public Mono<RatingResponseDto> submitRating(Long conversationId, Long customerId, RatingRequestDto request) {
        return conversationService.loadOwnedConversation(conversationId, customerId)
                .flatMap(conversation -> {
                    if (conversation.getStatus() != ConversationStatus.CLOSED) {
                        return Mono.<ChatConversation>error(new ForbiddenOperationException(
                                "You can only rate a conversation after it has been closed"));
                    }
                    return Mono.just(conversation);
                })
                .flatMap(conversation -> conversationRatingRepository.findByConversationId(conversationId)
                        .flatMap(existing -> Mono.<ChatConversation>error(new DuplicateResourceException(
                                "Conversation " + conversationId + " has already been rated")))
                        .switchIfEmpty(Mono.just(conversation)))
                .flatMap(conversation -> conversationRatingRepository.save(ConversationRating.builder()
                                .conversationId(conversationId)
                                .customerId(customerId)
                                .agentId(conversation.getAssignedAgentId())
                                .rating(request.getRating())
                                .feedback(request.getFeedback())
                                .createdAt(LocalDateTime.now())
                                .build())
                        .map(this::toDto));
    }

    private RatingResponseDto toDto(ConversationRating r) {
        return RatingResponseDto.builder()
                .conversationId(r.getConversationId())
                .customerId(r.getCustomerId())
                .agentId(r.getAgentId())
                .rating(r.getRating())
                .feedback(r.getFeedback())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
