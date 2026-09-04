package com.zivdah.inventory.client;

import com.zivdah.inventory.dto.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

// Synchronous, service-to-service call into product-service — used for (1) checking "does
// this VENDOR own the product they're trying to add stock for" before allowing it, since
// inventory-service has no vendorId of its own, only productId, and (2) pushing an updated
// availableQuantity back to product-service's stockQuantity whenever THIS service is the one
// that changed it (add/reserve/release), so the two stay in sync. Same pattern as
// zivdah-payment-service's OrderServiceClient.
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceClient {

    private static final String PRODUCT_SERVICE_URL = "http://localhost:8003/restful/v1/api/products";

    private final WebClient webClient;

    /**
     * Public endpoint, no auth needed. Returns empty (not an error) if the product doesn't
     * exist or the call fails — callers must treat "empty" as "deny", not "allow", since this
     * is used for an ownership check.
     */
    public Mono<Long> getProductVendorId(Long productId) {
        return webClient.get()
                .uri(PRODUCT_SERVICE_URL + "/{id}", productId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ProductSummary>>() {})
                .mapNotNull(resp -> resp.getData() != null ? resp.getData().getVendorId() : null)
                .onErrorResume(ex -> {
                    log.error("Failed to look up vendorId for product {}: {}", productId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fire-and-forget push: tells product-service to set its stockQuantity to match
     * inventory's new availableQuantity. Internal endpoint (no auth) — see
     * ProductController's PUT /products/{id}/sync-stock. Never fails the caller's own
     * inventory mutation if product-service is unreachable; just logs.
     */
    public Mono<Void> syncStockQuantity(Long productId, Integer availableQuantity) {
        return webClient.put()
                .uri(PRODUCT_SERVICE_URL + "/{id}/sync-stock", productId)
                .bodyValue(Map.of("stockQuantity", availableQuantity))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Product {} stockQuantity synced to {}", productId, availableQuantity))
                .onErrorResume(ex -> {
                    log.error("Failed to sync stockQuantity for product {} to {}: {}", productId, availableQuantity, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // Only the fields this check needs — Spring's default Jackson config ignores the rest of
    // product-service's real ProductResponseDto payload.
    @Getter
    @Setter
    public static class ProductSummary {
        private Long id;
        private Long vendorId;
    }
}
