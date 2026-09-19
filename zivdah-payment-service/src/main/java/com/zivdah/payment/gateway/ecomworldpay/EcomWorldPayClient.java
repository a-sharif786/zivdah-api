package com.zivdah.payment.gateway.ecomworldpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutRequest;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutStatusRequest;
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
    private final ObjectMapper objectMapper;

    @Value("${ecomworldpay.base-url}")
    private String baseUrl;

    @Value("${ecomworldpay.qr-intent-path}")
    private String qrIntentPath;

    @Value("${ecomworldpay.status-check-path}")
    private String statusCheckPath;

    @Value("${ecomworldpay.payout-path}")
    private String payoutPath;

    @Value("${ecomworldpay.payout-status-path}")
    private String payoutStatusPath;

    @Value("${ecomworldpay.tenant-id}")
    private String tenantId;

    @Value("${ecomworldpay.merchant-id}")
    private String merchantId;

    @Value("${ecomworldpay.api-key}")
    private String apiKey;

    @Value("${ecomworldpay.secret-key}")
    private String secretKey;

    public Mono<QrIntentResponse> createUpiIntent(QrIntentRequest request) {


        try {
            log.warn(
                    "EcomWorldPay payment not found: {}",
                    objectMapper.writeValueAsString(request)
            );
        } catch (JsonProcessingException e) {
            log.warn("EcomWorldPay payment not found: {}", request, e);
        }

        return webClient.post()
                .uri(baseUrl + qrIntentPath)
                .header("X-TenantID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorMap(WebClientResponseException.class,
                        ex -> new EcomWorldPayException(extractGatewayMessage(ex.getResponseBodyAsString()), ex))
                .flatMap(this::decodeQrIntentResponse)
                .doOnError(ex -> log.error("EcomWorldPay QR intent creation failed for invno {}: {}",
                        request.getInvno(), describeError(ex)));
    }

    // The documented path is a QR-intent JSON body (SUCCESS or FAIL, both under 200 OK), decoded
    // straight into QrIntentResponse so registerUpiIntent can read response.getMessage(). But
    // EcomWorldPay doesn't always send that shape — a rejected request can come back as a bare
    // string ("Your IP is not whitelisted.") with no "message" field to pull from. Either way,
    // extractGatewayMessage below surfaces exactly what the gateway sent, verbatim.
    private Mono<QrIntentResponse> decodeQrIntentResponse(String raw) {
        try {
            return Mono.just(objectMapper.readValue(raw, QrIntentResponse.class));
        } catch (JsonProcessingException e) {
            return Mono.error(new EcomWorldPayException(extractGatewayMessage(raw), e));
        }
    }

    // Pulls the gateway's own error text out of a response body of unknown shape: a "message"
    // field when the body is JSON (matching the documented FAIL response), otherwise the raw body
    // itself (the gateway's bare-string error responses, e.g. IP allowlist rejections).
    private String extractGatewayMessage(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "EcomWorldPay returned an empty error response";
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (JsonProcessingException ignored) {
            // Not JSON — the raw text itself is the message.
        }
        return trimmed;
    }


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
                .bodyToMono(String.class)
                .flatMap(raw -> decodeStatusCheckResponse(raw, gatewayTransactionId))
                .doOnError(ex -> {
                    if (!(ex instanceof TransactionNotYetAvailableException)) {
                        log.error("EcomWorldPay status check failed for gateway txn {}: {}",
                                gatewayTransactionId, describeError(ex));
                    }
                });
    }

    private Mono<EcomWorldPayTransactionDto> decodeStatusCheckResponse(String raw, String gatewayTransactionId) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("Data")) {
                String description = root.get("Data").path("Description").asText(null);
                log.info("EcomWorldPay transaction {} not resolvable yet: {}", gatewayTransactionId, description);
                return Mono.error(new TransactionNotYetAvailableException(description));
            }
            return Mono.just(objectMapper.treeToValue(root, EcomWorldPayTransactionDto.class));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException(
                    "Failed to parse EcomWorldPay status check response: " + raw, e));
        }
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public Mono<EcomWorldPayPayoutResponse> createPayout(EcomWorldPayPayoutRequest request) {
        return decodeBody(
                        webClient.post()
                                .uri(baseUrl + payoutPath)
                                .header("X-TenantID", tenantId)
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class),
                        EcomWorldPayPayoutResponse.class, "payout request")
                .doOnError(ex -> log.error("EcomWorldPay payout request failed for invoice {}: {}",
                        request.getInvoiceNumber(), describeError(ex)));
    }

    public Mono<EcomWorldPayPayoutResponse> checkPayoutStatus(String gatewayReferenceId) {
        EcomWorldPayPayoutStatusRequest request = EcomWorldPayPayoutStatusRequest.builder()
                .transactionId(gatewayReferenceId)
                .build();
        return decodeBody(
                        webClient.post()
                                .uri(baseUrl + payoutStatusPath)
                                .header("X-TenantID", tenantId)
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class),
                        EcomWorldPayPayoutResponse.class, "payout status check")
                .doOnError(ex -> log.error("EcomWorldPay payout status check failed for gateway ref {}: {}",
                        gatewayReferenceId, describeError(ex)));
    }

    // EcomWorldPay sometimes labels its (valid JSON) responses Content-Type: text/plain, which
    // Spring's Jackson decoder refuses to decode via bodyToMono(SomeDto.class) — it throws
    // UnsupportedMediaTypeException despite the response being 200 OK with a well-formed body.
    // Reading the body as a plain String sidesteps that media-type check, then we parse it
    // ourselves. retrieve()'s 4xx/5xx -> WebClientResponseException handling still applies
    // first, before this runs, regardless of the requested body type.
    private <T> Mono<T> decodeBody(Mono<String> rawBody, Class<T> type, String context) {
        return rawBody.flatMap(raw -> {
            try {
                return Mono.just(objectMapper.readValue(raw, type));
            } catch (JsonProcessingException e) {
                return Mono.error(new IllegalStateException(
                        "Failed to parse EcomWorldPay " + context + " response: " + raw, e));
            }
        });
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
