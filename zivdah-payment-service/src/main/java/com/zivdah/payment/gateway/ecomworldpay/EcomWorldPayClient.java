package com.zivdah.payment.gateway.ecomworldpay;

import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayTransactionDto;
import com.zivdah.payment.gateway.ecomworldpay.dto.QrIntentRequest;
import com.zivdah.payment.gateway.ecomworldpay.dto.QrIntentResponse;
import com.zivdah.payment.gateway.ecomworldpay.dto.TransactionStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

// EcomWorldPay UPI QR (PayIn) gateway client — QR intent creation + transaction status check.
// Credentials are externalized (see application-dev.yaml / application-prod.yaml, .env.example)
// same as OrderServiceClient's order-service.url, since these differ between environments and
// must never be committed.
@Service
@Slf4j
@RequiredArgsConstructor
public class EcomWorldPayClient {

    private final WebClient webClient;

    @Value("${ecomworldpay.base-url}")
    private String baseUrl;

    @Value("${ecomworldpay.qr-intent-path}")
    private String qrIntentPath;

    @Value("${ecomworldpay.status-check-path}")
    private String statusCheckPath;

    @Value("${ecomworldpay.tenant-id}")
    private String tenantId;

    @Value("${ecomworldpay.merchant-id}")
    private String merchantId;

    @Value("${ecomworldpay.api-key}")
    private String apiKey;

    @Value("${ecomworldpay.secret-key}")
    private String secretKey;

    public Mono<QrIntentResponse> createUpiIntent(QrIntentRequest request) {
        return webClient.post()
                .uri(baseUrl + qrIntentPath)
                .header("X-TenantID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QrIntentResponse.class)
                .doOnError(ex -> log.error("EcomWorldPay QR intent creation failed for invno {}: {}",
                        request.getInvno(), describeError(ex)));
    }

    // gatewayTransactionId is the gateway's own id (QrIntentResponse.transactionId /
    // EcomWorldPayTransactionDto.pgTxnId), NOT our internal Payment.transactionId.
    public Mono<EcomWorldPayTransactionDto> checkTransactionStatus(String gatewayTransactionId) {
        TransactionStatusRequest request = TransactionStatusRequest.builder()
                .transactionId(gatewayTransactionId)
                .merchantId(merchantId)
                .secretKey(secretKey)
                .apiKey(apiKey)
                .build();
        return webClient.post()
                .uri(baseUrl + statusCheckPath)
                .header("X-TenantID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EcomWorldPayTransactionDto.class)
                .doOnError(ex -> log.error("EcomWorldPay status check failed for gateway txn {}: {}",
                        gatewayTransactionId, describeError(ex)));
    }

    public String getMerchantId() {
        return merchantId;
    }

    // WebClientResponseException.getMessage() only carries the status line (e.g. "500 Internal
    // Server Error from POST ..."); the gateway's actual error reason is in the response body,
    // which is otherwise silently discarded.
    private static String describeError(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getMessage() + " - body: " + wcre.getResponseBodyAsString();
        }
        return ex.getMessage();
    }
}
