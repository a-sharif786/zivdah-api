package com.zivdah.delivery.client;

import com.zivdah.delivery.dto.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Synchronous, service-to-service call into order-service — used to resolve "which vendor(s)
// are on this order" when auto-creating one Delivery row per vendor once an order is
// CONFIRMED (see OrderEventConsumer). GET /orders/{orderId} is already public/permitAll and
// already returns items with vendorId per line item — same pattern as
// zivdah-notification-service's own copy of this client.
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceClient {

    private static final String ORDER_SERVICE_URL = "http://localhost:8005/restful/v1/api/orders";

    private final WebClient webClient;

    /** One entry per distinct vendor on the order, PLUS an {@code Optional.empty()} entry if
     *  any item is platform-owned (see OrderItemDto#vendorId — most products in this catalog
     *  have no vendor at all) — callers create one Delivery per distinct entry, empty included,
     *  so a platform-only order still gets a delivery record instead of silently getting none.
     *  Wrapped in Optional rather than a raw nullable Long because callers feed this into a
     *  reactive Flux (Flux.fromIterable) — Reactive Streams forbids null elements in a
     *  sequence, so the "no vendor" case has to be a real, non-null Optional.empty() object,
     *  not a null Long, or the Flux throws NullPointerException("iterator returned a null
     *  value") the moment it hits one (confirmed live: this exact NPE fired from
     *  createPendingDeliveriesForOrder before this method was wrapped in Optional).
     *  Empty list (not an error) only if the order doesn't exist, has no items, or the call
     *  fails — callers should treat that as "nothing to create", not retry. */
    public Mono<List<Optional<Long>>> getVendorIds(Long orderId) {
        return webClient.get()
                .uri(ORDER_SERVICE_URL + "/{orderId}", orderId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderSummary>>() {})
                .map(resp -> {
                    if (resp.getData() == null || resp.getData().getItems() == null) {
                        return List.<Optional<Long>>of();
                    }
                    List<Optional<Long>> vendorIds = resp.getData().getItems().stream()
                            .map(ItemSummary::getVendorId)
                            .map(Optional::ofNullable)
                            .distinct()
                            .toList();
                    log.info("Order {} resolved to vendor groups {} (empty = platform-owned items present)",
                            orderId, vendorIds);
                    return vendorIds;
                })
                .onErrorResume(ex -> {
                    log.error("Failed to look up vendor ids for order {}: {}", orderId, ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /** Best-effort sync into order-service's coarser Order.status (ON_THE_WAY ->
     *  OUT_FOR_DELIVERY, DELIVERED -> DELIVERED — see OrderServiceImpl#syncDeliveryStatus).
     *  Fire-and-forget: a failure here must never block the delivery-status transition that
     *  triggered it, Delivery is still the source of truth for fulfillment. */
    public Mono<Void> syncOrderDeliveryStatus(Long orderId, String deliveryStatus) {
        return webClient.put()
                .uri(ORDER_SERVICE_URL + "/{orderId}/delivery-status", orderId)
                .bodyValue(new DeliveryStatusSyncBody(deliveryStatus))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.error("Failed to sync order {} delivery status to {}: {}", orderId, deliveryStatus, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Getter
    @RequiredArgsConstructor
    public static class DeliveryStatusSyncBody {
        private final String deliveryStatus;
    }

    // Only the fields this needs — Spring's default Jackson config ignores the rest of
    // order-service's real OrderResponseDto payload.
    @Getter
    @Setter
    public static class OrderSummary {
        private Long orderId;
        private Long userId;
        private List<ItemSummary> items;
    }

    @Getter
    @Setter
    public static class ItemSummary {
        private Long vendorId;
    }
}
