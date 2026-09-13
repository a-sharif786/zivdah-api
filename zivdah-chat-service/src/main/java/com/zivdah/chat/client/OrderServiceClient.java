package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.OrderSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Synchronous, service-to-service call into order-service — backs the ORDER_STATUS,
 * CANCEL_ORDER and ORDER_HISTORY intents (see RuleBasedChatbotProvider) and the
 * confirm-and-cancel step behind POST /chat/bot/confirm (see ChatbotServiceImpl). Same
 * non-load-balanced-WebClient + externalized-{@code order-service.url} pattern as
 * zivdah-delivery-service's own copy of this client.
 *
 * <p>order-service enforces NO ownership check on any of these three endpoints (see
 * OrderController / OrderServiceImpl) — every caller here is responsible for comparing
 * {@code OrderSummaryDto#getUserId()} against the JWT-derived customerId before ever using or
 * acting on the result, and for checking {@code OrderSummaryDto#getStatus()} against the
 * cancellable set before calling {@link #cancelOrder(Long)}.
 *
 * <p>Errors/timeouts are deliberately left to propagate (no {@code onErrorResume} here) so
 * callers can fall back to the standard "I'm having trouble..." reply per the plan.
 *
 * <p><b>Auth note (verified against order-service's SecurityConfig):</b>
 * {@code GET /orders/{orderId}} is a single path segment, matched by order-service's
 * {@code /orders/*} permitAll wildcard, so {@link #getOrder(Long)} works with no auth header.
 * But {@code GET /orders/user/{userId}} and {@code PUT /orders/cancel/{orderId}} are
 * two-segment paths that do NOT match that wildcard and fall through to
 * {@code .anyExchange().authenticated()} — they require an authenticated caller. So
 * {@link #getOrdersByUser(Long, String)} and {@link #cancelOrder(Long, String)} forward the
 * customer's own bearer token (the same JWT chat-service already validated on the inbound
 * request — see ChatContext#bearerToken()) rather than calling unauthenticated.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceClient {

    @Value("${order-service.url}")
    private String orderServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<OrderSummaryDto> getOrder(Long orderId) {
        return webClient.get()
                .uri(orderServiceUrl + "/{orderId}", orderId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<OrderSummaryDto>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }

    public Mono<List<OrderSummaryDto>> getOrdersByUser(Long userId, String bearerToken) {
        return webClient.get()
                .uri(orderServiceUrl + "/user/{userId}", userId)
                .headers(headers -> {
                    if (bearerToken != null) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<OrderSummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }

    public Mono<Void> cancelOrder(Long orderId, String bearerToken) {
        return webClient.put()
                .uri(orderServiceUrl + "/cancel/{orderId}", orderId)
                .headers(headers -> {
                    if (bearerToken != null) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(TIMEOUT);
    }
}
