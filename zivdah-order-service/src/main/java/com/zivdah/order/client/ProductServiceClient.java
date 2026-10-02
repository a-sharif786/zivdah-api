package com.zivdah.order.client;

import com.zivdah.order.client.dto.ApiEnvelope;
import com.zivdah.order.client.dto.ProductPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

// Server-side source of truth for an order line's unit price and vendor (see
// OrderPricingService) — the checkout request's own price/vendorId fields are never trusted.
// Calls product-service's public GET /products/{id}, the same endpoint the storefront itself
// reads prices from. Unlike this service's other clients this one is NOT best-effort: without a
// trustworthy price there's no safe order to create, so any failure fails the order.
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Value("${product-service.url}")
    private String productServiceUrl;

    private final WebClient webClient;

    public Mono<ProductPriceDto> getProduct(Long productId) {
        return webClient.get()
                .uri(productServiceUrl + "/{id}", productId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<ProductPriceDto>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData)
                .onErrorMap(WebClientResponseException.NotFound.class, ex -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Product " + productId + " is no longer available"))
                .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> {
                    log.error("Product lookup for pricing failed (product {}): {}", productId, ex.getMessage());
                    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Could not verify product prices right now. Please try again.");
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Product " + productId + " is no longer available")));
    }
}
