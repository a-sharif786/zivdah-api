package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.dto.ConversationSummaryDto;
import com.zivdah.chat.dto.ws.MessagePayload;
import com.zivdah.chat.dto.ws.PresencePayload;
import com.zivdah.chat.dto.ws.WsEnvelope;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
import com.zivdah.chat.entity.ConversationRating;
import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.enums.ConversationType;
import com.zivdah.chat.enums.MessageStatus;
import com.zivdah.chat.enums.MessageType;
import com.zivdah.chat.enums.SenderType;
import com.zivdah.chat.exception.ConversationConflictException;
import com.zivdah.chat.exception.ForbiddenOperationException;
import com.zivdah.chat.exception.ResourceNotFoundException;
import com.zivdah.chat.kafka.ChatKafkaProducer;
import com.zivdah.chat.repository.ConversationRatingRepository;
import com.zivdah.chat.repository.ConversationRepository;
import com.zivdah.chat.repository.ConversationSearchRepository;
import com.zivdah.chat.repository.ConversationUpdateRepository;
import com.zivdah.chat.repository.MessageRepository;
import com.zivdah.chat.service.ConversationService;
import com.zivdah.chat.service.MessageService;
import com.zivdah.chat.service.SupportAgentService;
import com.zivdah.chat.websocket.SessionRegistry;
import com.zivdah.common.event.ChatConversationAcceptedEvent;
import com.zivdah.common.event.ChatConversationClosedEvent;
import com.zivdah.common.event.ChatHumanRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String HUMAN_REQUESTED_SYSTEM_MESSAGE = "Customer requested a human agent.";
    private static final String AGENT_JOINED_MESSAGE = "Agent has joined the chat.";
    private static final String CONVERSATION_CLOSED_MESSAGE = "Conversation closed.";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationUpdateRepository conversationUpdateRepository;
    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;
    private final ChatKafkaProducer chatKafkaProducer;
    private final SupportAgentService supportAgentService;
    private final ConversationRatingRepository conversationRatingRepository;
    private final ConversationSearchRepository conversationSearchRepository;

    @Override
    public Mono<ChatConversation> createConversation(Long customerId) {
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
                .customerId(customerId)
                .type(ConversationType.BOT)
                .status(ConversationStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return conversationRepository.save(conversation)
                .doOnSuccess(saved -> log.info("Created conversation {} for customer {}", saved.getId(), customerId));
    }

    @Override
    public Mono<ChatConversation> loadOwnedConversation(Long conversationId, Long customerId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    if (!conversation.getCustomerId().equals(customerId)) {
                        return Mono.error(new ForbiddenOperationException(
                                "You do not have access to this conversation"));
                    }
                    return Mono.just(conversation);
                });
    }

    @Override
    public Mono<ChatConversation> requestHumanHandoff(ChatConversation conversation) {
        LocalDateTime now = LocalDateTime.now();
        conversation.setType(ConversationType.HUMAN);
        conversation.setStatus(ConversationStatus.WAITING);
        conversation.setHandedOffAt(now);
        conversation.setUpdatedAt(now);

        return conversationRepository.save(conversation)
                .flatMap(saved -> messageRepository.save(ChatMessage.builder()
                                .conversationId(saved.getId())
                                .senderId(null)
                                .senderType(SenderType.SYSTEM)
                                .messageType(MessageType.SYSTEM)
                                .message(HUMAN_REQUESTED_SYSTEM_MESSAGE)
                                .status(MessageStatus.SENT)
                                .createdAt(now)
                                .build())
                        .then(publishHumanRequested(saved))
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Conversation {} handed off to human support", saved.getId()));
    }

    @Override
    public Mono<ChatConversation> loadConversationForViewer(Long conversationId, Long userId, String role) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    if ("ADMIN".equals(role) || conversation.getCustomerId().equals(userId)) {
                        return Mono.just(conversation);
                    }
                    return Mono.error(new ForbiddenOperationException(
                            "You do not have access to this conversation"));
                });
    }

    @Override
    public Flux<ChatMessage> loadMessages(Long conversationId, Long userId, String role, Long afterId,
                                           Pageable pageable) {
        long effectiveAfterId = afterId != null ? afterId : 0L;
        return loadConversationForViewer(conversationId, userId, role)
                .flatMapMany(conversation -> messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(
                        conversationId, effectiveAfterId, pageable));
    }

    @Override
    public Mono<ChatConversation> acceptConversation(Long conversationId, Long agentId) {
        // hasRole('ADMIN') alone (the @PreAuthorize on the controller) isn't enough — the caller
        // must also be an active support_agents roster entry (see the plan's security section).
        return requireActiveAgent(agentId, "accept")
                .then(Mono.defer(() -> conversationUpdateRepository.acceptIfWaiting(conversationId, agentId)))
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Mono.error(new ConversationConflictException(
                                "Conversation " + conversationId
                                        + " is no longer waiting — already claimed or closed."));
                    }
                    return conversationRepository.findById(conversationId)
                            .switchIfEmpty(Mono.error(
                                    new ResourceNotFoundException("Conversation not found: " + conversationId)));
                })
                .flatMap(conversation -> publishConversationAccepted(conversation, agentId).thenReturn(conversation))
                .flatMap(conversation -> messageService.persistSystemMessage(conversationId, AGENT_JOINED_MESSAGE)
                        .doOnNext(systemMessage -> {
                            sessionRegistry.broadcast(conversationId, toSystemEnvelope(systemMessage, conversation.getStatus()));
                            sessionRegistry.broadcast(conversationId, toPresenceEnvelope(true, "ADMIN"));
                        })
                        .thenReturn(conversation))
                .doOnSuccess(conversation -> log.info("Conversation {} accepted by agent {}", conversationId, agentId));
    }

    @Override
    public Mono<ChatConversation> closeConversation(Long conversationId, Long agentId) {
        return requireActiveAgent(agentId, "close")
                .then(Mono.defer(() -> conversationRepository.findById(conversationId)
                        .switchIfEmpty(Mono.error(
                                new ResourceNotFoundException("Conversation not found: " + conversationId)))))
                .flatMap(conversation -> {
                    if (conversation.getAssignedAgentId() == null
                            || !conversation.getAssignedAgentId().equals(agentId)) {
                        return Mono.error(new ForbiddenOperationException(
                                "Only the assigned agent can close this conversation"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    conversation.setStatus(ConversationStatus.CLOSED);
                    conversation.setClosedAt(now);
                    conversation.setUpdatedAt(now);
                    return conversationRepository.save(conversation);
                })
                .flatMap(conversation -> publishConversationClosed(conversation, agentId).thenReturn(conversation))
                .flatMap(conversation -> messageService.persistSystemMessage(conversationId, CONVERSATION_CLOSED_MESSAGE)
                        .doOnNext(systemMessage -> {
                            sessionRegistry.broadcast(conversationId, toSystemEnvelope(systemMessage, conversation.getStatus()));
                            sessionRegistry.broadcast(conversationId, toPresenceEnvelope(false, "ADMIN"));
                        })
                        .thenReturn(conversation))
                .doOnSuccess(conversation -> log.info("Conversation {} closed by agent {}", conversationId, agentId));
    }

    private Mono<Void> publishHumanRequested(ChatConversation conversation) {
        return Mono.fromRunnable(() -> chatKafkaProducer.publishHumanRequested(ChatHumanRequestedEvent.builder()
                .conversationId(conversation.getId())
                .customerId(conversation.getCustomerId())
                .orderId(conversation.getOrderId())
                .topic(conversation.getTopic())
                .build()));
    }

    private Mono<Void> publishConversationAccepted(ChatConversation conversation, Long agentId) {
        return supportAgentService.resolveDisplayName(agentId)
                .flatMap(agentDisplayName -> Mono.fromRunnable(() ->
                        chatKafkaProducer.publishConversationAccepted(ChatConversationAcceptedEvent.builder()
                                .conversationId(conversation.getId())
                                .customerId(conversation.getCustomerId())
                                .agentId(agentId)
                                .agentDisplayName(agentDisplayName)
                                .build())))
                .then();
    }

    // hasRole('ADMIN') alone is not sufficient to accept/close a conversation — the caller must
    // also be an active support_agents roster entry (see the plan's security section: agent
    // scoping is enforced here, not just via @PreAuthorize, since the roster is a chat-service-
    // only concept the security filter has no visibility into).
    private Mono<Void> requireActiveAgent(Long agentId, String action) {
        return supportAgentService.isActiveAgent(agentId)
                .flatMap(isActive -> isActive
                        ? Mono.<Void>empty()
                        : Mono.error(new ForbiddenOperationException(
                                "Only an active support agent can " + action + " conversations")));
    }

    private Mono<Void> publishConversationClosed(ChatConversation conversation, Long agentId) {
        // resolved is hardcoded true — there's no separate resolved/unresolved concept yet; this
        // could later become a parameter (e.g. an agent-supplied outcome on close).
        return Mono.fromRunnable(() -> chatKafkaProducer.publishConversationClosed(
                ChatConversationClosedEvent.builder()
                        .conversationId(conversation.getId())
                        .customerId(conversation.getCustomerId())
                        .agentId(agentId)
                        .resolved(true)
                        .build()));
    }

    // conversationStatus rides alongside the message's own status (SENT/DELIVERED/READ) — the
    // customer-facing widget (zivdah-web ChatContext) watches for conversationStatus === "CLOSED"
    // on SYSTEM frames specifically to leave HUMAN mode and return to the bot; passing the real
    // ChatConversation#getStatus() here (ACTIVE on accept, CLOSED on close) is what makes that
    // detection actually fire instead of silently never matching.
    private WsEnvelope toSystemEnvelope(ChatMessage systemMessage, ConversationStatus conversationStatus) {
        return WsEnvelope.builder()
                .type("SYSTEM")
                .payload(MessagePayload.builder()
                        .id(systemMessage.getId())
                        .conversationId(systemMessage.getConversationId())
                        .senderId(systemMessage.getSenderId())
                        .senderType(systemMessage.getSenderType().name())
                        .messageType(systemMessage.getMessageType().name())
                        .message(systemMessage.getMessage())
                        .attachmentUrl(systemMessage.getAttachmentUrl())
                        .status(systemMessage.getStatus().name())
                        .conversationStatus(conversationStatus != null ? conversationStatus.name() : null)
                        .createdAt(systemMessage.getCreatedAt())
                        .build())
                .build();
    }

    private WsEnvelope toPresenceEnvelope(boolean online, String role) {
        return WsEnvelope.builder().type("PRESENCE").payload(new PresencePayload(online, role)).build();
    }

    // --- Phase 6: list/queue/search views ---

    @Override
    public Flux<ConversationSummaryDto> getWaiting(Pageable pageable) {
        return conversationRepository.findByStatusOrderByUpdatedAtDesc(ConversationStatus.WAITING, pageable)
                .flatMap(this::toSummaryDto);
    }

    @Override
    public Flux<ConversationSummaryDto> getMine(Long agentId, Pageable pageable) {
        return conversationRepository.findByAssignedAgentIdOrderByUpdatedAtDesc(agentId, pageable)
                .flatMap(this::toSummaryDto);
    }

    @Override
    public Flux<ConversationSummaryDto> getAll(ConversationStatus status, Pageable pageable) {
        Flux<ChatConversation> conversations = status != null
                ? conversationRepository.findByStatusOrderByUpdatedAtDesc(status, pageable)
                : conversationRepository.findAllByOrderByUpdatedAtDesc(pageable);
        return conversations.flatMap(this::toSummaryDto);
    }

    @Override
    public Flux<ConversationSummaryDto> getCustomerConversations(Long customerId, Pageable pageable) {
        return conversationRepository.findByCustomerIdOrderByUpdatedAtDesc(customerId, pageable)
                .flatMap(this::toSummaryDto);
    }

    @Override
    public Flux<ConversationSummaryDto> search(Long customerId, Long orderId, ConversationStatus status, Long agentId,
                                                LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return conversationSearchRepository.search(customerId, orderId, status, agentId, from, to, pageable)
                .flatMap(this::toSummaryDto);
    }

    private Mono<ConversationSummaryDto> toSummaryDto(ChatConversation c) {
        Mono<String> lastMessageMono = messageRepository.findFirstByConversationIdOrderByIdDesc(c.getId())
                .map(ChatMessage::getMessage)
                .defaultIfEmpty("");
        // Optional<Short> is the wrapper: Reactor's Mono can't itself emit null, but the DTO field
        // legitimately is null until a CLOSED conversation gets rated.
        Mono<Optional<Short>> ratingMono = conversationRatingRepository.findByConversationId(c.getId())
                .map(ConversationRating::getRating)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return lastMessageMono.zipWith(ratingMono, (lastMessage, ratingOpt) -> ConversationSummaryDto.builder()
                .id(c.getId())
                .customerId(c.getCustomerId())
                .type(c.getType() != null ? c.getType().name() : null)
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .assignedAgentId(c.getAssignedAgentId())
                .orderId(c.getOrderId())
                .topic(c.getTopic())
                .lastMessagePreview(lastMessage)
                .unreadCount(0)
                .rating(ratingOpt.orElse(null))
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .closedAt(c.getClosedAt())
                .build());
    }
}
