package com.zivdah.chat.service;

import com.zivdah.chat.dto.RatingRequestDto;
import com.zivdah.chat.dto.RatingResponseDto;
import reactor.core.publisher.Mono;

public interface RatingService {

    // POST /conversations/{id}/rating — customer-owned, only once a conversation is CLOSED
    // (ForbiddenOperationException/403 otherwise), one rating per conversation
    // (DuplicateResourceException/409 on a second attempt).
    Mono<RatingResponseDto> submitRating(Long conversationId, Long customerId, RatingRequestDto request);
}
