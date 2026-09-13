package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.InternalUserInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Synchronous, service-to-service call into auth-service's existing internal, unauthenticated
 * {@code GET /auth/internal/users/{userId}} endpoint (see AuthController#getInternalUserInfo) —
 * the exact mechanism zivdah-order-service already uses to resolve a display name for an
 * arbitrary userId when printing an invoice (no user JWT available in that context either).
 * Added now as forward-looking scaffolding: not invoked by any Phase 4 intent yet (no intent in
 * this phase personalizes a reply with the customer's display name), but ready for a future
 * order-context panel that needs to resolve a vendor/delivery-boy/customer name from an id.
 *
 * <p>Chosen deliberately over a {@code UserServiceClient}: user-service's only relevant
 * endpoint ({@code GET /user/getProfile}) derives the target userId strictly from the caller's
 * own JWT ({@code Long.valueOf(auth.getName())}) and requires {@code hasAnyRole('USER','ADMIN')}
 * with no arbitrary-userId variant — see zivdah-user-service's UserController/SecurityConfig.
 * It cannot resolve an arbitrary customer's profile without owning that customer's own JWT
 * (which chat-service never has), so a {@code UserServiceClient} was skipped entirely rather
 * than wired to an endpoint that would either 401 or silently return the wrong user's data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceClient {

    @Value("${auth-service.url}")
    private String authServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<InternalUserInfoDto> getInternalUserInfo(Long userId) {
        return webClient.get()
                .uri(authServiceUrl + "/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<InternalUserInfoDto>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }

    /**
     * Used by WaitingConversationEscalationScheduler to find who to nag about a stale WAITING
     * conversation. Same internal, unauthenticated {@code GET /auth/internal/admin-ids} endpoint
     * zivdah-notification-service's own AuthServiceClient already calls for the same reason.
     * Empty list (not an error) on failure — the scheduler should treat that as "nothing to
     * notify this pass", not fail the whole run.
     */
    public Mono<List<Long>> getAdminUserIds() {
        return webClient.get()
                .uri(authServiceUrl + "/internal/admin-ids")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<Long>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData)
                .defaultIfEmpty(List.of())
                .onErrorResume(ex -> {
                    log.warn("Failed to look up admin ids: {}", ex.toString());
                    return Mono.just(Collections.emptyList());
                });
    }
}
