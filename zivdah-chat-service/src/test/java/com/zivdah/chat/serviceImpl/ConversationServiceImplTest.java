package com.zivdah.chat.serviceImpl;

import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.entity.ChatMessage;
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
import com.zivdah.chat.service.MessageService;
import com.zivdah.chat.service.SupportAgentService;
import com.zivdah.chat.websocket.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Covers the two pieces of Phase 2 logic that are pure enough to unit test without a real DB:
// (1) the USER-owns-it/ADMIN-sees-anything visibility rule behind GET /conversations/{id} and
// /messages, and (2) the rows-affected conflict check behind POST /conversations/{id}/accept that
// exists specifically to avoid a two-agent race.
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationUpdateRepository conversationUpdateRepository;
    @Mock
    private MessageService messageService;
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private ChatKafkaProducer chatKafkaProducer;
    @Mock
    private SupportAgentService supportAgentService;
    @Mock
    private ConversationRatingRepository conversationRatingRepository;
    @Mock
    private ConversationSearchRepository conversationSearchRepository;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, messageRepository,
                conversationUpdateRepository, messageService, sessionRegistry, chatKafkaProducer,
                supportAgentService, conversationRatingRepository, conversationSearchRepository);
    }

    private ChatConversation conversation(Long customerId, Long assignedAgentId, ConversationStatus status) {
        return ChatConversation.builder()
                .id(42L)
                .customerId(customerId)
                .type(ConversationType.HUMAN)
                .status(status)
                .assignedAgentId(assignedAgentId)
                .build();
    }

    @Test
    void loadConversationForViewer_allowsOwningCustomer() {
        when(conversationRepository.findById(42L))
                .thenReturn(Mono.just(conversation(100L, null, ConversationStatus.WAITING)));

        StepVerifier.create(service.loadConversationForViewer(42L, 100L, "USER"))
                .expectNextMatches(c -> c.getId().equals(42L))
                .verifyComplete();
    }

    @Test
    void loadConversationForViewer_rejectsNonOwningCustomer() {
        when(conversationRepository.findById(42L))
                .thenReturn(Mono.just(conversation(100L, null, ConversationStatus.WAITING)));

        StepVerifier.create(service.loadConversationForViewer(42L, 999L, "USER"))
                .expectError(ForbiddenOperationException.class)
                .verify();
    }

    @Test
    void loadConversationForViewer_allowsAdminRegardlessOfOwnership() {
        when(conversationRepository.findById(42L))
                .thenReturn(Mono.just(conversation(100L, 7L, ConversationStatus.ACTIVE)));

        StepVerifier.create(service.loadConversationForViewer(42L, 999L, "ADMIN"))
                .expectNextMatches(c -> c.getId().equals(42L))
                .verifyComplete();
    }

    @Test
    void loadConversationForViewer_notFoundIsResourceNotFound() {
        when(conversationRepository.findById(42L)).thenReturn(Mono.empty());

        StepVerifier.create(service.loadConversationForViewer(42L, 100L, "USER"))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void acceptConversation_conflictWhenAlreadyClaimedByAnotherAgent() {
        when(supportAgentService.isActiveAgent(7L)).thenReturn(Mono.just(true));
        // 0 rows affected == another agent's accept already won the race.
        when(conversationUpdateRepository.acceptIfWaiting(42L, 7L)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.acceptConversation(42L, 7L))
                .expectError(ConversationConflictException.class)
                .verify();
    }

    @Test
    void acceptConversation_rejectsAgentNotOnActiveRoster() {
        when(supportAgentService.isActiveAgent(7L)).thenReturn(Mono.just(false));

        StepVerifier.create(service.acceptConversation(42L, 7L))
                .expectError(ForbiddenOperationException.class)
                .verify();
    }

    @Test
    void acceptConversation_succeedsAndPersistsSystemMessageWhenRowUpdated() {
        when(supportAgentService.isActiveAgent(7L)).thenReturn(Mono.just(true));
        when(conversationUpdateRepository.acceptIfWaiting(42L, 7L)).thenReturn(Mono.just(1L));
        when(conversationRepository.findById(42L))
                .thenReturn(Mono.just(conversation(100L, 7L, ConversationStatus.ACTIVE)));
        when(supportAgentService.resolveDisplayName(7L)).thenReturn(Mono.just("Jane"));
        when(messageService.persistSystemMessage(eq(42L), any()))
                .thenReturn(Mono.just(ChatMessage.builder()
                        .id(1L).conversationId(42L).senderType(SenderType.SYSTEM)
                        .messageType(MessageType.SYSTEM).message("Agent has joined the chat.")
                        .status(MessageStatus.SENT).build()));

        StepVerifier.create(service.acceptConversation(42L, 7L))
                .expectNextMatches(c -> c.getStatus() == ConversationStatus.ACTIVE
                        && c.getAssignedAgentId().equals(7L))
                .verifyComplete();
    }
}
