package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.PaymentSummaryDto;
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
 * Synchronous, service-to-service call into payment-service. Not invoked by any Phase 4 intent
 * today — REFUND_REQUEST never calls a payment endpoint (no self-service refund exists; see
 * RuleBasedChatbotProvider) — added now as forward-looking scaffolding per the plan's client
 * list, ready for a future order-context aggregation panel that needs a payment summary
 * alongside order/delivery data.
 *
 * <p><b>Known gap (verified against payment-service's SecurityConfig):</b>
 * {@code GET /payments/order/{orderId}} is a two-segment path that does NOT match
 * payment-service's {@code /payments/*} permitAll wildcard and falls through to
 * {@code .anyExchange().authenticated()} — it requires an authenticated caller. This client
 * sends no Authorization header, so a future caller of {@link #getPaymentsByOrder(Long)} will
 * need to address that the same way noted on {@code OrderServiceClient}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceClient {

    @Value("${payment-service.url}")
    private String paymentServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<List<PaymentSummaryDto>> getPaymentsByOrder(Long orderId) {
        return webClient.get()
                .uri(paymentServiceUrl + "/order/{orderId}", orderId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<PaymentSummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }
}
