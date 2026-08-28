package com.zivdah.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String ORDER_SERVICE_URL = "http://localhost:8005/restful/v1/api/orders";

    private final WebClient webClient;

    public Mono<Void> updatePaymentStatus(Long orderId, String status) {
        return webClient.put()
                .uri(ORDER_SERVICE_URL + "/{orderId}/payment-status", orderId)
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
