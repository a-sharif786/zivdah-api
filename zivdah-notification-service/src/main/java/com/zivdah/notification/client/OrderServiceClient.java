package com.zivdah.notification.client;

import com.zivdah.notification.dto.ApiResponse;
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
import java.util.Objects;

// Synchronous, service-to-service call into order-service — used to resolve "which vendor(s)
// are on this order" for the events (order-created, payment-completed, order-status-changed)
// that don't carry vendorId themselves. GET /orders/{orderId} is already public/permitAll and
// already returns items with vendorId per line item, so no order-service change is needed —
// this is purely new plumbing on the notification-service side.
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceClient {

    private static final String ORDER_SERVICE_URL = "http://localhost:8005/restful/v1/api/orders";

    private final WebClient webClient;

    /** Empty list (not an error) if the order doesn't exist, has no vendor-owned items, or
     *  the call fails — callers should treat that as "nothing to notify", not retry. */
    public Mono<List<Long>> getVendorIds(Long orderId) {
        return webClient.get()
                .uri(ORDER_SERVICE_URL + "/{orderId}", orderId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderSummary>>() {})
                .map(resp -> {
                    if (resp.getData() == null || resp.getData().getItems() == null) {
                        return List.<Long>of();
                    }
                    return resp.getData().getItems().stream()
                            .map(ItemSummary::getVendorId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                })
                .onErrorResume(ex -> {
                    log.error("Failed to look up vendor ids for order {}: {}", orderId, ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
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
