package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.ProductSummaryDto;
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
 * Synchronous, service-to-service call into product-service — backs the PRODUCT_SEARCH intent
 * (see RuleBasedChatbotProvider). Both {@code GET /products/search} and
 * {@code GET /products/category/{category}} are permitAll in product-service's SecurityConfig,
 * so this client needs no Authorization header to work. Price-ceiling/organic filtering is done
 * in-memory by the caller — no server-side filter exists for either (see the plan's list of
 * real API gaps this feature designs around).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceClient {

    @Value("${product-service.url}")
    private String productServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<List<ProductSummaryDto>> searchByKeyword(String keyword, int page, int size) {
        return webClient.get()
                .uri(productServiceUrl + "/search?keyword={keyword}&page={page}&size={size}",
                        keyword, page, size)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<ProductSummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }

    public Mono<List<ProductSummaryDto>> searchByCategory(String category, int page, int size) {
        return webClient.get()
                .uri(productServiceUrl + "/category/{category}?page={page}&size={size}",
                        category, page, size)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<ProductSummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }
}
