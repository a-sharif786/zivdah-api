package com.zivdah.order.client;

import com.zivdah.order.client.dto.ApiEnvelope;
import com.zivdah.order.client.dto.CouponDto;
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

// Reads a coupon's definition so OrderPricingService can recompute its discount server-side
// instead of trusting the checkout request's discountAmount. Uses coupon-service's read-only
// GET /coupons/{code} — NOT POST /apply, which increments the coupon's usedCount and has already
// been called once by the storefront when the customer applied the code.
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Value("${coupon-service.url}")
    private String couponServiceUrl;

    private final WebClient webClient;

    public Mono<CouponDto> getCoupon(String code) {
        return webClient.get()
                .uri(couponServiceUrl + "/{code}", code)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<CouponDto>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData)
                .onErrorMap(WebClientResponseException.NotFound.class, ex -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Coupon " + code + " is not valid"))
                .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> {
                    log.error("Coupon lookup for pricing failed (coupon {}): {}", code, ex.getMessage());
                    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Could not verify the coupon right now. Please try again.");
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Coupon " + code + " is not valid")));
    }
}
