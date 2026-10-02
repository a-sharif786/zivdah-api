package com.zivdah.order.client;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.order.client.dto.ApiEnvelope;
import com.zivdah.order.client.dto.PaymentSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

// Synchronous, service-to-service call into payment-service so admin-refunding an order (via
// OrderServiceImpl#updateStatus) also refunds the underlying payment, instead of leaving the
// dashboard's "Payment Received" figure blind to it. Best-effort: a payment-service hiccup here
// must not fail the order status transition itself — see the identical pattern/reasoning in
// payment-service's own OrderServiceClient.
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceClient {

    // Externalized per-profile (see application-dev.yaml / application-prod.yaml) — must not be
    // hardcoded to localhost, which only resolves in dev/UAT where every service is a separate
    // process on one host (see zivdah-delivery-service's identical mistake, which silently
    // dropped delivery-row creation in production).
    @Value("${payment-service.url}")
    private String paymentServiceUrl;

    // payment-service's /order/{orderId}/refund is internal-only (hasRole SERVICE) — see
    // zivdah-common InternalServiceAuthenticationFilter.
    @Value("${internal.api-token}")
    private String internalApiToken;

    private final WebClient webClient;

    public Mono<Void> refundOrderPayment(Long orderId) {
        return webClient.put()
                .uri(paymentServiceUrl + "/order/{orderId}/refund", orderId)
                .header(InternalAuth.HEADER, internalApiToken)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Refunded payment for order {}", orderId))
                .onErrorResume(ex -> {
                    log.error("Failed to refund payment for order {}: {}", orderId, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // payment-service's own record of an order's payments — used to verify an (unauthenticated)
    // payment-completed Kafka event before acting on it (see PaymentCompletedConsumer). Empty on
    // any failure, so the caller treats "couldn't verify" as "don't act".
    public Mono<List<PaymentSummaryDto>> getPaymentsForOrder(Long orderId) {
        return webClient.get()
                .uri(paymentServiceUrl + "/order/{orderId}", orderId)
                .header(InternalAuth.HEADER, internalApiToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<PaymentSummaryDto>>>() {})
                .timeout(Duration.ofSeconds(5))
                .mapNotNull(ApiEnvelope::getData)
                .onErrorResume(ex -> {
                    log.error("Failed to fetch payments for order {}: {}", orderId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
