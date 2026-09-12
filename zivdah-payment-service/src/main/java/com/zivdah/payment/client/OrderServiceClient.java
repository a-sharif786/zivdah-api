package com.zivdah.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
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
        return updatePaymentStatus(orderId, status, null, null, null);
    }

    // paymentMethod/transactionId/paidAt are carried along on the PAID transition so
    // order-service can generate an invoice without a second round-trip back into
    // payment-service just to read them (see InvoiceService#generateInvoice there — it has no
    // user JWT to call payment-service's own authenticated endpoints from that internal context).
    // All three are optional; order-service falls back to "unknown"/generation-time values
    // when they're absent (e.g. the CANCELLED/REFUNDED calls above, which don't set them).
    public Mono<Void> updatePaymentStatus(
            Long orderId, String status, String paymentMethod, String transactionId, LocalDateTime paidAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("paymentMethod", paymentMethod);
        body.put("transactionId", transactionId);
        body.put("paidAt", paidAt);
        return webClient.put()
                .uri(orderServiceUrl + "/{orderId}/payment-status", orderId)
                .bodyValue(body)
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
