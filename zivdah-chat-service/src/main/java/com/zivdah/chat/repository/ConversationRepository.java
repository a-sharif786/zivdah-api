package com.zivdah.chat.repository;

import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.enums.ConversationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ConversationRepository extends ReactiveCrudRepository<ChatConversation, Long> {

    // Owner-scoped fetch — used by read endpoints that should 404 rather than leak whether a
    // conversation exists at all when it isn't the caller's own (later phases: GET
    // /conversations/{id}, /messages, etc.). The bot-message flow itself loads by id and checks
    // ownership explicitly so it can tell "not found" (404) apart from "not yours" (403) — see
    // ConversationServiceImpl#loadOwnedConversation.
    Mono<ChatConversation> findByIdAndCustomerId(Long id, Long customerId);

    // Backs WaitingConversationEscalationScheduler — conversations nobody has claimed within the
    // stale threshold of their BOT->HUMAN handoff, and not already escalated once before.
    Flux<ChatConversation> findByStatusAndHandedOffAtBeforeAndEscalatedAtIsNull(
            ConversationStatus status, LocalDateTime threshold);

    // --- List/queue views (Phase 6) ---

    // GET /conversations/waiting — the shared queue every roster'd agent can see.
    Flux<ChatConversation> findByStatusOrderByUpdatedAtDesc(ConversationStatus status, Pageable pageable);

    // GET /conversations/mine — this agent's own conversations across every status.
    Flux<ChatConversation> findByAssignedAgentIdOrderByUpdatedAtDesc(Long assignedAgentId, Pageable pageable);

    // GET /conversations/all?status= — ADMIN-only oversight, optionally status-filtered.
    Flux<ChatConversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    // GET /conversations/customer/me — the customer's own history.
    Flux<ChatConversation> findByCustomerIdOrderByUpdatedAtDesc(Long customerId, Pageable pageable);
}
