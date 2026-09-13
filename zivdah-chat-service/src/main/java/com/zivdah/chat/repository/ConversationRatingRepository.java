package com.zivdah.chat.repository;

import com.zivdah.chat.entity.ConversationRating;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ConversationRatingRepository extends ReactiveCrudRepository<ConversationRating, Long> {

    // conversation_id is UNIQUE — one rating per conversation (Phase 6: POST /conversations/{id}/rating).
    Mono<ConversationRating> findByConversationId(Long conversationId);
}
