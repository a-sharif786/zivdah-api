package com.zivdah.chat.client;

import com.zivdah.chat.security.JwtTokenProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Synchronous, service-to-service call into notification-service's direct REST endpoint — backs
 * {@code WaitingConversationEscalationScheduler}'s "waiting conversation needs attention" alert
 * (a poll-triggered condition, not a domain event, so a direct REST call is the simpler fit than
 * another Kafka round-trip). Unlike every other client in this package, notification-service's
 * SecurityConfig requires an authenticated caller for {@code POST /notifications/send} with no
 * internal/permitAll exemption — and this scheduler has no per-user JWT to forward (it's a cron
 * job, not a request). So it mints its own short-lived system token via {@link JwtTokenProvider}
 * (the same shared {@code jwt.secret} every service trusts) rather than touching
 * notification-service's SecurityConfig.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceClient {

    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    private final WebClient webClient;
    private final JwtTokenProvider jwtTokenProvider;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String SYSTEM_SUBJECT = "zivdah-chat-service-scheduler";

    /** Fire-and-forget-ish: logs and swallows failures rather than propagating, since one admin's
     *  notification failing shouldn't abort the escalation pass for the others. */
    public Mono<Void> send(SendRequest request) {
        String systemToken = jwtTokenProvider.generateToken(SYSTEM_SUBJECT);
        return webClient.post()
                .uri(notificationServiceUrl + "/send")
                .headers(headers -> headers.setBearerAuth(systemToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("Failed to send escalation notification to user {}: {}", request.getUserId(), ex.toString());
                    return Mono.empty();
                });
    }

    @Getter
    @Setter
    public static class SendRequest {
        private Long userId;
        private String title;
        private String message;
        private String recipientRole;
        private String notificationType;
        private String entityType;
        private Long entityId;
        private String dedupKey;
    }
}
