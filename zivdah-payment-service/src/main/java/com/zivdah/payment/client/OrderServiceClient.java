package com.zivdah.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

// Synchronous, service-to-service call into order-service so a payment result updates the
// order's status within the same request the checkout flow already awaits, instead of relying
// solely on the async "payment-completed" Kafka event (which has no retry and can be silently
// lost — see PaymentKafkaProducer). The Kafka event is still published alongside this as a
// best-effort backstop.
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceClient {

    // Externalized per-profile (see application-dev.yaml / application-prod.yaml) — must not be
    // hardcoded to localhost:8005, which only resolves in dev/UAT where every service is a
    // separate process on one host. In production each service is its own Docker container, so
    // localhost would resolve to this container itself and the call would fail every time (see
    // zivdah-delivery-service's identical OrderServiceClient, where this exact hardcoding
    // silently dropped delivery-row creation in production).
    @Value("${order-service.url}")
    private String orderServiceUrl;

    private final WebClient webClient;

    public Mono<Void> updatePaymentStatus(Long orderId, String status) {
        return webClient.put()
                .uri(orderServiceUrl + "/{orderId}/payment-status", orderId)
                .bodyValue(Map.of("status", status))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Order {} payment-status updated to {}", orderId, status))
                .onErrorResume(ex -> {
                    // Don't fail the payment operation itself if order-service is unreachable —
                    // the payment result is already persisted, and the Kafka event (published
                    // alongside this call) can still bring the order's status up to date later.
                    log.error("Failed to update order {} payment-status to {}: {}", orderId, status, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
