package com.zivdah.auth.sms;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Local-dev stand-in: no SMS is sent and the OTP is always 123456. Never enable in production —
// application-prod.yaml defaults otp.sms.provider to "authkey".
@Component
@ConditionalOnProperty(name = "otp.sms.provider", havingValue = "static", matchIfMissing = true)
@Slf4j
public class StaticSmsOtpProvider implements SmsOtpProvider {

    static final String STATIC_OTP = "123456";

    @PostConstruct
    void warn() {
        log.warn("Static OTP provider active — no SMS is sent and every mobile OTP is {}. Dev only.", STATIC_OTP);
    }

    @Override
    public Mono<String> send(String mobile) {
        return Mono.just("static-" + UUID.randomUUID());
    }

    @Override
    public Mono<OtpCheckResult> verify(String mobile, String reference, String otp) {
        return Mono.just(STATIC_OTP.equals(otp) ? OtpCheckResult.VALID : OtpCheckResult.INVALID);
    }
}
