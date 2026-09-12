package com.zivdah.order.client;

import com.zivdah.order.client.dto.ApiEnvelope;
import com.zivdah.order.client.dto.CustomerInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

// Synchronous, service-to-service call into auth-service so InvoiceService can print the
// customer's name/email on a generated invoice PDF — order-service otherwise only ever stores
// a userId, never a name/email snapshot (see Order entity). Best-effort: a auth-service hiccup
// here must not block invoice generation (the order/payment data is already confirmed) — falls
// back to a placeholder name, same reasoning as OrderServiceClient/PaymentServiceClient's other
// best-effort cross-service calls.
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceClient {

    // Externalized per-profile (see application-dev.yaml / application-prod.yaml) — must not be
    // hardcoded to localhost, which only resolves in dev/UAT where every service is a separate
    // process on one host (see zivdah-delivery-service's identical mistake in production).
    @Value("${auth-service.url}")
    private String authServiceUrl;

    private final WebClient webClient;

    public Mono<CustomerInfoDto> getCustomerInfo(Long userId) {
        return webClient.get()
                .uri(authServiceUrl + "/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<CustomerInfoDto>>() {})
                .map(ApiEnvelope::getData)
                .onErrorResume(ex -> {
                    log.error("Failed to fetch customer info for invoice (user {}): {}", userId, ex.getMessage());
                    return Mono.just(CustomerInfoDto.builder()
                            .id(userId).name("Customer #" + userId).email(null).mobile(null).build());
                });
    }
}
