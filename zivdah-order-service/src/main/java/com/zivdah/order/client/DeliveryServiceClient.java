package com.zivdah.order.client;

import com.zivdah.order.client.dto.ApiEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Answers "is this delivery boy assigned to this order?" for OrderController's access check —
// the delivery-boy portal (zivdah-admin useMyDeliveries) reads GET /orders/{orderId} for each of
// its deliveries. Rather than duplicating assignment data here, this forwards the caller's own
// bearer token to delivery-service's GET /delivery/order/{orderId}, which already filters its
// result to deliveries visible to that caller (a DELIVERY_BOY sees only rows assigned to them —
// see DeliveryServiceImpl#isVisibleTo), so a non-empty answer means "assigned".
@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Value("${delivery-service.url}")
    private String deliveryServiceUrl;

    private final WebClient webClient;

    public Mono<Boolean> isAssignedToCaller(Long orderId, String bearerToken) {
        if (bearerToken == null) {
            return Mono.just(false);
        }
        return webClient.get()
                .uri(deliveryServiceUrl + "/order/{orderId}", orderId)
                .headers(h -> h.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<Map<String, Object>>>>() {})
                .timeout(TIMEOUT)
                .map(env -> env.getData() != null && !env.getData().isEmpty())
                .defaultIfEmpty(false)
                // Fail closed: if delivery-service can't confirm the assignment, deny access.
                .onErrorResume(ex -> {
                    log.warn("Delivery assignment check failed for order {}: {}", orderId, ex.getMessage());
                    return Mono.just(false);
                });
    }
}
