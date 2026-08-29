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

// Synchronous, service-to-service call into product-service so InventoryController can check
// "does this VENDOR own the product they're trying to add stock for" before allowing it —
// inventory-service has no vendorId of its own, only productId. Same pattern as
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

    // Only the fields this check needs — Spring's default Jackson config ignores the rest of
    // product-service's real ProductResponseDto payload.
    @Getter
    @Setter
    public static class ProductSummary {
        private Long id;
        private Long vendorId;
    }
}
