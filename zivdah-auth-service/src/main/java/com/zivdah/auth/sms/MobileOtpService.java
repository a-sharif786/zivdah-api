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

    public Mono<Void> send(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.claimOtpSend(user.getId(), now, now.minus(resendCooldown))
                .flatMap(claimed -> {
                    if (claimed == 0) {
                        return Mono.<Integer>error(cooldown(user, now));
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
                        : smsOtpProvider.verify(user.getMobile(), ref, otp))
                .flatMap(result -> switch (result) {
                    case VALID -> userRepository.clearOtpRef(user.getId(), ref).then();
                    case INVALID -> Mono.<Void>error(new OtpException(HttpStatus.BAD_REQUEST, INVALID));
                    case EXPIRED -> userRepository.clearOtpRef(user.getId(), ref)
                            .then(Mono.<Void>error(new OtpException(HttpStatus.BAD_REQUEST, REQUEST_NEW)));
                });
    }

    private OtpException cooldown(UserEntity user, LocalDateTime now) {
        long waited = user.getOtpGeneratedAt() == null ? 0 : Duration.between(user.getOtpGeneratedAt(), now).toSeconds();
        long wait = Math.max(1, resendCooldown.toSeconds() - waited);
        return new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                "Please wait " + wait + " seconds before requesting another OTP.");
    }
}
