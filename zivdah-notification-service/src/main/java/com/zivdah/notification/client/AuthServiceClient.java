package com.zivdah.notification.client;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.notification.dto.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

// Synchronous, service-to-service call into auth-service â€” used to fan out "Admin" recipient
// notifications to every ADMIN-role user. Calls the internal, unauthenticated
// GET /auth/internal/admin-ids endpoint (see AuthController) since this is a Kafka consumer
// with no user JWT to present.
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceClient {

    // Externalized per-profile like every other service's auth-service.url (was hardcoded to
    // localhost:8002, which in the prod Docker network resolves to this container itself â€” so
    // admin fan-out and push-token lookups silently failed in production).
    @Value("${auth-service.internal-url}")
    private String authServiceUrl;

    // Internal, service-to-service calls authenticate with the shared internal token (see
    // zivdah-common InternalServiceAuthenticationFilter) â€” those endpoints are no longer permitAll().
    @Value("${internal.api-token}")
    private String internalApiToken;

    private final WebClient webClient;

    /** Empty list (not an error) if the call fails â€” callers should treat that as "nothing to
     *  notify", not retry. */
    public Mono<List<Long>> getAdminUserIds() {
        return webClient.get()
                .uri(authServiceUrl + "/internal/admin-ids")
                .header(InternalAuth.HEADER, internalApiToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {})
                .map(resp -> resp.getData() != null ? resp.getData() : List.<Long>of())
                .onErrorResume(ex -> {
                    log.error("Failed to look up admin ids: {}", ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /** Resolves every active FCM token for a user â€” they may be signed in on several
     *  devices/browsers at once â€” so a push reaches all of them. Empty list (not an error)
     *  if the user has none registered or the call fails â€” callers should treat that as
     *  "can't push this one", not retry. */
    public Mono<List<String>> getActiveDeviceTokens(Long userId) {
        return webClient.get()
                .uri(authServiceUrl + "/internal/device-tokens/" + userId)
                .header(InternalAuth.HEADER, internalApiToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<String>>>() {})
                .map(resp -> resp.getData() != null ? resp.getData() : List.<String>of())
                .onErrorResume(ex -> {
                    log.error("Failed to look up device tokens for user {}: {}", userId, ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /** Fire-and-forget: called when Firebase reports a token as unregistered/invalid, so
     *  future sends stop targeting it. A failure here is logged only â€” it just means that
     *  one dead token gets retried (and fails again) next time, not worth retrying itself. */
    public Mono<Void> deactivateToken(String fcmToken) {
        return webClient.patch()
                .uri(authServiceUrl + "/internal/device-tokens/deactivate")
                .header(InternalAuth.HEADER, internalApiToken)
                .bodyValue(new DeactivateBody(fcmToken))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.error("Failed to deactivate device token: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    @Getter
    @RequiredArgsConstructor
    private static class DeactivateBody {
        private final String fcmToken;
    }
}
