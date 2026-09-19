package com.zivdah.payment.client;

import com.zivdah.payment.client.dto.ApiEnvelope;
import com.zivdah.payment.client.dto.VendorBankDetailsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

// Synchronous, service-to-service call into auth-service so VendorPayoutService can read a
// vendor's bank account/UPI VPA on file before submitting a payout to EcomWorldPay — same
// pattern as zivdah-order-service's AuthServiceClient (customer info for invoices), reading
// the same internal endpoint. Unlike that one, this is NOT best-effort: a payout cannot
// proceed without real bank details, so a failure here propagates rather than falling back
// to a placeholder.
@Service
@RequiredArgsConstructor
public class AuthServiceClient {

    // Externalized per-profile (see application-dev.yaml / application-prod.yaml) — must stay
    // on the internal network in prod (AUTH_SERVICE_INTERNAL_URL), never the public gateway,
    // since it hits an unauthenticated /internal/** endpoint.
    @Value("${auth-service.url}")
    private String authServiceUrl;

    private final WebClient webClient;

    public Mono<VendorBankDetailsDto> getVendorBankDetails(Long vendorId) {
        return webClient.get()
                .uri(authServiceUrl + "/internal/users/{userId}", vendorId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<VendorBankDetailsDto>>() {})
                .map(ApiEnvelope::getData);
    }
}
