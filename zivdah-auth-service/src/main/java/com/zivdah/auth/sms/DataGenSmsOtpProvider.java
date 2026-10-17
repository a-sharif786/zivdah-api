package com.zivdah.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.auth.exception.OtpException;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// DataGen 2Factor API: generate_otp.php sends the OTP (backend-fixed template) and returns a logid;
// verify_otp.php checks the user's OTP against that logid. Selected with otp.sms.provider=datagen.
// Errors come back as HTTP 200 + {"status":"failure","code":4xx,...}. The server IP must be
// whitelisted in the DataGen panel (otherwise code 407).
@Component
@ConditionalOnProperty(name = "otp.sms.provider", havingValue = "datagen")
@Slf4j
public class DataGenSmsOtpProvider implements SmsOtpProvider {

    static final String UNAVAILABLE = "Unable to send or verify OTP right now. Please try again shortly.";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String authKey;
    private final String senderId;
    private final String countryCode;
    private final String entityId;
    private final String templateId;
    private final Duration timeout;

    public DataGenSmsOtpProvider(
            ObjectMapper objectMapper,
            @Value("${otp.sms.datagen.base-url:${DATAGEN_BASE_URL:https://global.datagenit.com/API}}") String baseUrl,
            @Value("${otp.sms.datagen.auth-key:${DATAGEN_AUTH_KEY:}}") String authKey,
            @Value("${otp.sms.datagen.sender-id:${DATAGEN_SENDER_ID:}}") String senderId,
            @Value("${otp.sms.datagen.country-code:${DATAGEN_COUNTRY_CODE:91}}") String countryCode,
            @Value("${otp.sms.datagen.entity-id:${DATAGEN_ENTITY_ID:}}") String entityId,
            @Value("${otp.sms.datagen.template-id:${DATAGEN_TEMPLATE_ID:}}") String templateId,
            @Value("${otp.sms.datagen.timeout:10s}") Duration timeout) {
        if (authKey == null || authKey.isBlank() || senderId == null || senderId.isBlank()) {
            throw new IllegalStateException(
                    "otp.sms.provider=datagen requires DATAGEN_AUTH_KEY and DATAGEN_SENDER_ID to be set");
        }
        // Plain builder, NOT the (possibly @LoadBalanced) WebClient.Builder bean — external host.
        // JDK/OS DNS resolver instead of Netty's own: Netty's asks the DNS server for AAAA records
        // directly, and some networks (10.10.10.135 locally) never answer, failing every call after 5s.
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)))
                .build();
        this.objectMapper = objectMapper;
        this.authKey = authKey.trim();
        this.senderId = senderId.trim();
        this.countryCode = countryCode == null || countryCode.isBlank() ? "91" : countryCode.replaceAll("\\D", "");
        this.entityId = entityId == null ? "" : entityId.trim();
        this.templateId = templateId == null ? "" : templateId.trim();
        this.timeout = timeout;
        log.info("DataGen OTP provider active (base-url={}, sender-id={}, template-id set={})",
                baseUrl, this.senderId, !this.templateId.isBlank());
    }

    @Override
    public Mono<String> send(String mobile) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("auth", authKey);
        // DataGen: "don't include country code with mobile number" — it goes in countrycode.
        params.put("msisdn", localNumber(mobile, countryCode));
        params.put("senderid", senderId);
        params.put("countrycode", countryCode);
        if (!entityId.isBlank()) {
            params.put("entity_id", entityId);
        }
        if (!templateId.isBlank()) {
            params.put("template_id", templateId);
        }
        return call("/generate_otp.php", params).map(DataGenSmsOtpProvider::interpretSend);
    }

    @Override
    public Mono<OtpCheckResult> verify(String mobile, String reference, String otp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("auth", authKey);
        params.put("msisdn", localNumber(mobile, countryCode));
        params.put("logid", reference);
        params.put("otp", otp);
        return call("/verify_otp.php", params).map(DataGenSmsOtpProvider::interpretVerify);
    }

    // Values go through URI-template variables so they're percent-encoded.
    private Mono<JsonNode> call(String path, Map<String, String> params) {
        String query = params.keySet().stream().map(k -> k + "={" + k + "}").collect(Collectors.joining("&"));
        return webClient.get()
                .uri(path + "?" + query, params)
                .retrieve()
                .bodyToMono(String.class) // don't trust the content type
                .timeout(timeout)
                .map(this::parse)
                .onErrorMap(e -> !(e instanceof OtpException), e -> {
                    // Never log e.getMessage(): WebClient puts the full URL (with the auth key) in it.
                    log.error("datagen {} call failed: {}{}{}", path, e.getClass().getSimpleName(),
                            e instanceof WebClientResponseException w
                                    ? " HTTP " + w.getStatusCode().value() + " body=" + abbreviate(w.getResponseBodyAsString())
                                    : "",
                            rootCause(e));
                    return new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
                });
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body.trim());
        } catch (Exception e) {
            log.error("datagen returned a non-JSON response: {}", abbreviate(body));
            throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    // {"status":"success","logid":"5feb7b51aca0d","desc":"OTP Sent","code":100,...}
    static String interpretSend(JsonNode r) {
        String logId = r.path("logid").asText("");
        if (isSuccess(r) && !logId.isBlank()) {
            return logId;
        }
        // 401/402/412 auth, 405 params, 407 IP not whitelisted, 408/413 sender id, 420 entity,
        // 429/436 balance — all ours to fix, none the user's.
        log.error("datagen generate_otp failed: {}", abbreviate(r.toString()));
        throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    // {"status":"success","code":200,"desc":"OTP verified successfully",...}
    static OtpCheckResult interpretVerify(JsonNode r) {
        if (isSuccess(r)) {
            return OtpCheckResult.VALID;
        }
        return switch (r.path("code").asText("")) {
            case "419" -> OtpCheckResult.INVALID;           // invalid otp
            case "421", "422", "418" -> OtpCheckResult.EXPIRED; // time limit / already verified / bad logid
            default -> {
                log.error("datagen verify_otp failed: {}", abbreviate(r.toString()));
                throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
            }
        };
    }

    // DataGen takes the number and country code separately: "+91 98765-43210" -> "9876543210".
    static String localNumber(String mobile, String countryCode) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() > 10 && digits.startsWith(countryCode)) {
            return digits.substring(countryCode.length());
        }
        return digits.startsWith("0") ? digits.substring(1) : digits;
    }

    // Connection-level failures (DNS, TLS, connect, proxy) hide the reason in the cause chain.
    // The top-level message carries the URL, so only causes are logged, with auth=... masked anyway.
    private static String rootCause(Throwable e) {
        Throwable t = e.getCause();
        if (t == null) {
            return "";
        }
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = String.valueOf(t.getMessage()).replaceAll("auth=[^&\\s]*", "auth=***");
        return " cause=" + t.getClass().getName() + ": " + abbreviate(msg);
    }

    private static boolean isSuccess(JsonNode r) {
        return "success".equalsIgnoreCase(r.path("status").asText(""));
    }

    private static String abbreviate(String s) {
        String flat = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
