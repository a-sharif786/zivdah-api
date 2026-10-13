package com.zivdah.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zivdah.auth.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
@Component
@ConditionalOnProperty(name = "otp.sms.provider", havingValue = "authkey")
@Slf4j
public class AuthKeySmsOtpProvider implements SmsOtpProvider {

    static final String UNAVAILABLE = "Unable to send or verify OTP right now. Please try again shortly.";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String authKey;
    private final String sid;
    private final String countryCode;
    private final Duration timeout;

    public AuthKeySmsOtpProvider(
            ObjectMapper objectMapper,
            @Value("${otp.sms.authkey.base-url:https://console.authkey.io}") String baseUrl,
            @Value("${otp.sms.authkey.auth-key}") String authKey,
            @Value("${otp.sms.authkey.sid}") String sid,
            @Value("${otp.sms.authkey.country-code:91}") String countryCode,
            @Value("${otp.sms.authkey.timeout:10s}") Duration timeout) {
        if (authKey == null || authKey.isBlank() || sid == null || sid.isBlank()) {
            throw new IllegalStateException(
                    "otp.sms.provider=authkey requires AUTHKEY_AUTH_KEY and AUTHKEY_SID to be set");
        }
        // Plain builder, NOT the (possibly @LoadBalanced) WebClient.Builder bean — this is an
        // external host, not a Eureka service.
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.authKey = authKey.trim();
        this.sid = sid.trim();
        this.countryCode = countryCode == null || countryCode.isBlank() ? "91" : countryCode.replaceAll("\\D", "");
        this.timeout = timeout;
    }

    @Override
    public Mono<String> send(String mobile) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("authkey", authKey);
        params.put("mobile", localNumber(mobile, countryCode));
        params.put("country_code", countryCode);
        params.put("sid", sid);
        return call("/restapi/request.php", params).map(AuthKeySmsOtpProvider::interpretSend);
    }

    @Override
    public Mono<OtpCheckResult> verify(String mobile, String reference, String otp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("authkey", authKey);
        params.put("channel", "SMS");
        params.put("otp", otp);
        params.put("logid", reference);
        return call("/api/2fa_verify.php", params).map(AuthKeySmsOtpProvider::interpretVerify);
    }

    // Values go through URI-template variables so they're percent-encoded (an auth key or OTP
    // containing '&' or '{' can't break the query).
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
                    log.error("authkey {} call failed: {}{}", path, e.getClass().getSimpleName(),
                            e instanceof WebClientResponseException w
                                    ? " HTTP " + w.getStatusCode().value() + " body=" + abbreviate(w.getResponseBodyAsString())
                                    : "");
                    return new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
                });
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body.trim());
        } catch (Exception e) {
            log.error("authkey returned a non-JSON response: {}", abbreviate(body));
            throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    // {"LogID":"28bf7375bb54540ba03a4eb873d4da44","Message":"Submitted Successfully"}
    static String interpretSend(JsonNode r) {
        String logId = field(r, "LogID");
        if (!logId.isBlank()) {
            return logId;
        }
        // No LogID = not sent: bad authkey/sid, no balance, DLT rejection — all ours to fix.
        log.error("authkey request.php failed: {}", abbreviate(r.toString()));
        throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    // {"status":true,"message":"Valid OTP"}. Failure wording isn't documented, so classify by
    // keywords; anything unrecognised counts as a wrong OTP (the attempt cap still applies) and is
    // logged so the mapping can be tightened.
    static OtpCheckResult interpretVerify(JsonNode r) {
        if (r.path("status").asBoolean(false)) {
            return OtpCheckResult.VALID;
        }
        String message = field(r, "message").toLowerCase(Locale.ROOT);
        if (message.contains("authkey") || message.contains("auth key") || message.contains("balance")
                || message.contains("whitelist") || message.contains("ip address")) {
            log.error("authkey 2fa_verify failed: {}", abbreviate(r.toString()));
            throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        if (message.contains("expire") || message.contains("already") || message.contains("logid")) {
            return OtpCheckResult.EXPIRED;
        }
        if (!message.contains("invalid otp")) {
            log.warn("authkey 2fa_verify: unrecognised failure treated as wrong OTP: {}", abbreviate(r.toString()));
        }
        return OtpCheckResult.INVALID;
    }

    // authkey takes the number and country code separately: "+91 98765-43210" -> "9876543210".
    static String localNumber(String mobile, String countryCode) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() > 10 && digits.startsWith(countryCode)) {
            return digits.substring(countryCode.length());
        }
        return digits.startsWith("0") ? digits.substring(1) : digits;
    }

    // authkey's casing isn't consistent across endpoints (LogID / logid, Message / message).
    private static String field(JsonNode r, String name) {
        return r.properties().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(e -> e.getValue().asText(""))
                .findFirst()
                .orElse("");
    }

    private static String abbreviate(String s) {
        String flat = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
