package com.zivdah.chat.client;

import com.zivdah.chat.client.dto.ApiEnvelope;
import com.zivdah.chat.client.dto.CouponSummaryDto;
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
 * Synchronous, service-to-service call into coupon-service — backs the OFFERS intent (see
 * RuleBasedChatbotProvider). Calls the new {@code GET /coupons/active} companion endpoint
 * (Phase 4, part B) — permitAll (matches coupon-service's existing single-path-segment
 * permitAll rule that already covers {@code GET /coupons/{code}}), so this client needs no
 * Authorization header to work.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceClient {

    @Value("${coupon-service.url}")
    private String couponServiceUrl;

    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public Mono<List<CouponSummaryDto>> getActiveOffers() {
        return webClient.get()
                .uri(couponServiceUrl + "/active")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<CouponSummaryDto>>>() {})
                .timeout(TIMEOUT)
                .mapNotNull(ApiEnvelope::getData);
    }
}
