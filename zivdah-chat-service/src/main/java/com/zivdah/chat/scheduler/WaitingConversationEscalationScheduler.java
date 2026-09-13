package com.zivdah.chat.scheduler;

import com.zivdah.chat.client.AuthServiceClient;
import com.zivdah.chat.client.NotificationServiceClient;
import com.zivdah.chat.entity.ChatConversation;
import com.zivdah.chat.enums.ConversationStatus;
import com.zivdah.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

// Every ~2 minutes: WAITING conversations nobody has claimed within 5 minutes of their BOT->HUMAN
// handoff get a one-time "needs attention" notification to every admin — escalated_at guards
// against re-nagging the same conversation on every subsequent tick (see V2__add_escalated_at.sql).
// Mirrors NotificationRetryScheduler's in-process @Scheduled pattern (zivdah-notification-service)
// — same "enough at this app's scale" reasoning applies here.
@Component
@Slf4j
@RequiredArgsConstructor
public class WaitingConversationEscalationScheduler {

    private final ConversationRepository conversationRepository;
    private final AuthServiceClient authServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    @Scheduled(fixedDelay = 2 * 60 * 1000)
    public void escalateStaleWaitingConversations() {
        LocalDateTime threshold = LocalDateTime.now().minus(STALE_THRESHOLD);
        conversationRepository.findByStatusAndHandedOffAtBeforeAndEscalatedAtIsNull(ConversationStatus.WAITING, threshold)
                .flatMap(this::escalate)
                .doOnError(e -> log.error("Waiting-conversation escalation pass failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .blockLast();
    }

    private Mono<Void> escalate(ChatConversation conversation) {
        log.info("Escalating stale WAITING conversation {} (waiting since {})",
                conversation.getId(), conversation.getHandedOffAt());
        return authServiceClient.getAdminUserIds()
                .flatMapMany(Flux::fromIterable)
                .flatMap(adminId -> notificationServiceClient.send(buildRequest(conversation, adminId)))
                .then(markEscalated(conversation));
    }

    private NotificationServiceClient.SendRequest buildRequest(ChatConversation conversation, Long adminId) {
        NotificationServiceClient.SendRequest request = new NotificationServiceClient.SendRequest();
        request.setUserId(adminId);
        request.setTitle("Waiting conversation needs attention");
        request.setMessage("Conversation #" + conversation.getId()
                + " has been waiting for a human agent for over 5 minutes.");
        request.setRecipientRole("ADMIN");
        request.setNotificationType("CHAT_WAITING_ESCALATED");
        request.setEntityType("CHAT_CONVERSATION");
        request.setEntityId(conversation.getId());
        request.setDedupKey("CHAT_WAITING_ESCALATED:" + conversation.getId() + ":" + adminId);
        return request;
    }

    private Mono<Void> markEscalated(ChatConversation conversation) {
        conversation.setEscalatedAt(LocalDateTime.now());
        return conversationRepository.save(conversation).then();
    }
}
