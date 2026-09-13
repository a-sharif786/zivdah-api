package com.zivdah.chat.repository;

import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.SenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageRepository extends ReactiveCrudRepository<ChatMessage, Long> {

    // Full transcript for a conversation, oldest first (e.g. GET /conversations/{id}).
    Flux<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    // Incremental history / reconnect-replay source (WS reconnect — unbounded, since a replay
    // backlog on a single reconnect is expected to be small; the same conditions with a Pageable
    // below back the paged REST endpoint).
    Flux<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId);

    // Same incremental query, page-limited — GET /conversations/{id}/messages?afterId=&page=&size=.
    Flux<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId, Pageable pageable);

    // Last-message preview for conversation list/summary rows (mine/waiting/all/search).
    Mono<ChatMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    // The bot's own last turn, regardless of what's been persisted since (e.g. the customer's
    // just-saved inbound message) — lets RuleBasedChatbotProvider recognize a follow-up reply
    // (a bare order number) to its own previous "which order?" prompt. See ChatbotServiceImpl#
    // runBot and ChatContext#recentHistory.
    Mono<ChatMessage> findFirstBySenderTypeAndConversationIdOrderByIdDesc(SenderType senderType, Long conversationId);
}
