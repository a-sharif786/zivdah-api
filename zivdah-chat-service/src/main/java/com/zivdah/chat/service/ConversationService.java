package com.zivdah.chat.service;

import com.zivdah.chat.dto.ConversationSummaryDto;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.enums.ConversationStatus;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ConversationService {

    // Starts a fresh BOT/OPEN conversation for this customer.
    Mono<ChatConversation> createConversation(Long customerId);

    // Loads a conversation by id and verifies conversation.customerId == customerId —
    // ResourceNotFoundException (404) if no such conversation exists at all,
    // ForbiddenOperationException (403) if it exists but belongs to someone else. Never trusts a
    // client-supplied id without this check.
    Mono<ChatConversation> loadOwnedConversation(Long conversationId, Long customerId);

    // Handoff mechanics (used by both the bot flow's requestHandoff and, in Phase 3, the explicit
    // POST /conversations/{id}/request-human endpoint): transitions the same conversation row to
    // type=HUMAN, status=WAITING, handed_off_at=now(), and inserts a SYSTEM message into the same
    // messages table/conversation_id — this is the entire mechanism by which an agent later sees
    // the full prior bot transcript (every read is WHERE conversation_id=:id ORDER BY id).
    Mono<ChatConversation> requestHumanHandoff(ChatConversation conversation);

    // Phase 2 additions ------------------------------------------------------------------------

    // GET /conversations/{id}: a USER may view only their own conversation; ADMIN may view any
    // (oversight). ResourceNotFoundException (404) if it doesn't exist at all,
    // ForbiddenOperationException (403) for a USER who doesn't own it.
    Mono<ChatConversation> loadConversationForViewer(Long conversationId, Long userId, String role);

    // GET /conversations/{id}/messages?afterId=&page=&size= — same visibility rule as above,
    // applied before reading the (paged) transcript after afterId, oldest first.
    Flux<ChatMessage> loadMessages(Long conversationId, Long userId, String role, Long afterId, Pageable pageable);

    // POST /conversations/{id}/accept — atomic WAITING -> ACTIVE claim (see
    // ConversationUpdateRepository; avoids a two-agent race), then a SYSTEM message + PRESENCE
    // broadcast on success. Throws ConversationConflictException (409) if another agent already
    // claimed it first (0 rows affected).
    Mono<ChatConversation> acceptConversation(Long conversationId, Long agentId);

    // PUT /conversations/{id}/close — verifies the caller is the assigned agent
    // (ForbiddenOperationException/403 otherwise), transitions to CLOSED, then a SYSTEM message +
    // PRESENCE broadcast. This is the HUMAN -> BOT reset point: the customer's next bot message
    // starts a fresh BOT-type conversation.
    Mono<ChatConversation> closeConversation(Long conversationId, Long agentId);

    // Phase 6 additions — list/queue/search views, each row enriched with a last-message preview
    // and (once rated) the customer's rating. -------------------------------------------------

    // GET /conversations/waiting — the shared queue every roster'd agent can see.
    Flux<ConversationSummaryDto> getWaiting(Pageable pageable);

    // GET /conversations/mine — this agent's own conversations across every status.
    Flux<ConversationSummaryDto> getMine(Long agentId, Pageable pageable);

    // GET /conversations/all?status= — ADMIN-only oversight; status null means every status.
    Flux<ConversationSummaryDto> getAll(ConversationStatus status, Pageable pageable);

    // GET /conversations/customer/me — the caller's own history.
    Flux<ConversationSummaryDto> getCustomerConversations(Long customerId, Pageable pageable);

    // GET /conversations/search?... — structured filters only (see ConversationSearchRepository's
    // javadoc for why a free-text message search isn't included here).
    Flux<ConversationSummaryDto> search(Long customerId, Long orderId, ConversationStatus status, Long agentId,
                                         LocalDateTime from, LocalDateTime to, Pageable pageable);
}
