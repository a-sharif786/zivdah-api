package com.zivdah.product.client;

import com.zivdah.product.dto.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

// Synchronous, service-to-service call into inventory-service — used to (1) enrich product
// reads with the real availableQuantity, and (2) push a new stockQuantity to inventory
// whenever THIS service is the one that changed it (product create/update), so the two stay
// in sync. Same pattern as zivdah-payment-service's OrderServiceClient /
// zivdah-inventory-service's ProductServiceClient.
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceClient {

    // Externalized per-profile (see application-dev.yaml / application-prod.yaml) — must not be
    // hardcoded to localhost:8008, which only resolves in dev/UAT where every service is a
    // separate process on one host. In production each service is its own Docker container, so
    // localhost would resolve to this container itself and the call would fail every time (see
    // zivdah-delivery-service's OrderServiceClient, where this exact hardcoding silently
    // dropped delivery-row creation in production).
    @Value("${inventory-service.url}")
    private String inventoryServiceUrl;

    private final WebClient webClient;

    /**
     * Returns empty if inventory-service is unreachable or has no row for this product yet
     * (e.g. right after creation, before the async product-created event lands) — callers
     * should fall back to the product's own stockQuantity in that case.
     */
    public Mono<Integer> getAvailableQuantity(Long productId) {
        return webClient.get()
                .uri(inventoryServiceUrl + "/{productId}", productId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<InventorySummary>>() {})
                .mapNotNull(resp -> resp.getData() != null ? resp.getData().getAvailableQuantity() : null)
                .onErrorResume(ex -> {
                    log.warn("Failed to look up availableQuantity for product {}: {}", productId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fire-and-forget push: tells inventory-service to set availableQuantity to match this
     * product's new stockQuantity. Internal endpoint (no auth) — see InventoryController's
     * PUT /inventory/{productId}/sync-quantity. Never fails the caller's own product update
     * if inventory-service is unreachable; just logs.
     */
    public Mono<Void> setAvailableQuantitySync(Long productId, Integer stockQuantity) {
        return webClient.put()
                .uri(inventoryServiceUrl + "/{productId}/sync-quantity", productId)
                .bodyValue(Map.of("availableQuantity", stockQuantity != null ? stockQuantity : 0))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Inventory availableQuantity synced to {} for product {}", stockQuantity, productId))
                .onErrorResume(ex -> {
                    log.error("Failed to sync availableQuantity for product {} to {}: {}", productId, stockQuantity, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // Only the fields this needs — Spring's default Jackson config ignores the rest of
    // inventory-service's real InventoryResponseDto payload.
    @Getter
    @Setter
    public static class InventorySummary {
        private Long productId;
        private Integer availableQuantity;
    }
}
