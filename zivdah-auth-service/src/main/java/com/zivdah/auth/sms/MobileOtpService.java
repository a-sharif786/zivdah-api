package com.zivdah.auth.sms;

import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.exception.OtpException;
import com.zivdah.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Mobile OTP for login (/send-otp, /verify-otp) and registration (/register,
// /verify-registration-otp). The SmsOtpProvider generates/checks the OTP; this class adds the
// guards the provider doesn't give us:
//  - resend cooldown per user — /send-otp is public and every SMS costs money (SMS pumping);
//  - max wrong guesses per issued OTP, reserved atomically BEFORE asking the provider, so
//    parallel guesses can't exceed it; then the OTP is dropped and a new one must be requested.
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileOtpService {

    static final String INVALID = "Invalid OTP";
    static final String REQUEST_NEW = "OTP expired or too many attempts. Please request a new OTP.";

    private final SmsOtpProvider smsOtpProvider;
    private final UserRepository userRepository;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${otp.resend-cooldown:60s}")
    private Duration resendCooldown;

    // Demo/test numbers (e.g. for store reviewers): no SMS is sent and the fixed code below is
    // accepted. Cooldown and attempt cap still apply. Matched on the last 10 digits.
    @Value("${otp.demo.mobiles:}")
    private List<String> demoMobiles;

    @Value("${otp.demo.code:123456}")
    private String demoCode;

    private static final String DEMO_REF = "DEMO";

    public Mono<Void> send(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.claimOtpSend(user.getId(), now, now.minus(resendCooldown))
                .flatMap(claimed -> {
                    if (claimed == 0) {
                        return Mono.<Integer>error(cooldown(user, now));
                    }
                    if (isDemo(user.getMobile())) {
                        return userRepository.storeOtpRef(user.getId(), DEMO_REF);
                    }
                    return smsOtpProvider.send(user.getMobile())
                            .flatMap(ref -> userRepository.storeOtpRef(user.getId(), ref))
                            .onErrorResume(e -> userRepository.releaseOtpSend(user.getId()).then(Mono.error(e)));
                })
                .doOnSuccess(v -> log.info("Mobile OTP sent for user id={}", user.getId()))  // never the mobile or OTP
                .then();
    }

    // Completes empty when the OTP is right (and consumes it); errors otherwise.
    public Mono<Void> verify(UserEntity user, String otp) {
        String ref = user.getMobileOtpRef();
        if (ref == null) {
            return Mono.error(new OtpException(HttpStatus.BAD_REQUEST, REQUEST_NEW));
        }
        return userRepository.reserveOtpAttempt(user.getId(), ref, maxAttempts)
                .flatMap(reserved -> reserved == 0
                        ? userRepository.clearOtpRef(user.getId(), ref)
                                .then(Mono.<SmsOtpProvider.OtpCheckResult>error(new OtpException(HttpStatus.BAD_REQUEST, REQUEST_NEW)))
                        : DEMO_REF.equals(ref) && isDemo(user.getMobile())
                                ? Mono.just(demoCode.equals(otp) ? SmsOtpProvider.OtpCheckResult.VALID : SmsOtpProvider.OtpCheckResult.INVALID)
                                : smsOtpProvider.verify(user.getMobile(), ref, otp))
                .flatMap(result -> switch (result) {
                    case VALID -> userRepository.clearOtpRef(user.getId(), ref).then();
                    case INVALID -> Mono.<Void>error(new OtpException(HttpStatus.BAD_REQUEST, INVALID));
                    case EXPIRED -> userRepository.clearOtpRef(user.getId(), ref)
                            .then(Mono.<Void>error(new OtpException(HttpStatus.BAD_REQUEST, REQUEST_NEW)));
                });
    }

    private boolean isDemo(String mobile) {
        if (mobile == null || demoMobiles == null || demoMobiles.isEmpty()) {
            return false;
        }
        String last10 = lastTenDigits(mobile);
        return demoMobiles.stream().map(MobileOtpService::lastTenDigits).anyMatch(last10::equals);
    }

    private static String lastTenDigits(String mobile) {
        String digits = mobile.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private OtpException cooldown(UserEntity user, LocalDateTime now) {
        long waited = user.getOtpGeneratedAt() == null ? 0 : Duration.between(user.getOtpGeneratedAt(), now).toSeconds();
        long wait = Math.max(1, resendCooldown.toSeconds() - waited);
        return new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                "Please wait " + wait + " seconds before requesting another OTP.");
    }
}
