package com.zivdah.payment.client;

import com.zivdah.payment.client.dto.ApiEnvelope;
import com.zivdah.payment.client.dto.VendorBankDetailsDto;
import com.zivdah.common.security.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class AuthServiceClient {

    @Value("${auth-service.internal-url}")
    private String authServiceUrl;

    @Value("${internal.api-token}")
    private String internalApiToken;

    private final WebClient webClient;

    public Mono<VendorBankDetailsDto> getVendorBankDetails(Long vendorId) {
        return webClient.get()
                .uri(authServiceUrl + "/internal/users/{userId}", vendorId)
                .header(InternalAuth.HEADER, internalApiToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<VendorBankDetailsDto>>() {})
                .map(ApiEnvelope::getData);
    }
}
