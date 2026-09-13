package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.DeliverySummaryDto;
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
 * Synchronous, service-to-service call into delivery-service — backs the DELIVERY_TIME intent
 * when {@code context.orderId} is present (see RuleBasedChatbotProvider). Errors/timeouts are
 * deliberately left to propagate (no {@code onErrorResume} here) so the caller can fall back to
 * the standard "I'm having trouble..." reply.
 *
 * <p><b>Auth note (verified against delivery-service's SecurityConfig):</b> unlike
 * order-service/payment-service/product-service, delivery-service's SecurityConfig has no
 * permitAll/internal exemption at all beyond swagger — {@code GET /delivery/order/{orderId}}
 * falls under a plain {@code .anyExchange().authenticated()}, and delivery-service does its own
 * server-side visibility filtering by caller identity (customer/vendor/admin). So
 * {@link #getDeliveriesByOrder(Long, String)} forwards the customer's own bearer token rather
 * than calling unauthenticated.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryServiceClient {

    @Value("${delivery-service.url}")
    private String deliveryServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<List<DeliverySummaryDto>> getDeliveriesByOrder(Long orderId, String bearerToken) {
        return webClient.get()
                .uri(deliveryServiceUrl + "/order/{orderId}", orderId)
                .headers(headers -> {
                    if (bearerToken != null) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<DeliverySummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }
}
