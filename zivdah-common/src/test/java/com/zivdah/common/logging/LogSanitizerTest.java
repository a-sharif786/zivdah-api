package com.zivdah.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void masksJwtsAndBearerTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjcsInJvbGUiOiJBRE1JTiJ9.abcDEF123_-xyz";
        assertThat(LogSanitizer.sanitize("got " + jwt)).isEqualTo("got [JWT]").doesNotContain("eyJ");
        assertThat(LogSanitizer.sanitize("Authorization: Bearer abc.def.ghi")).isEqualTo("Authorization: Bearer [REDACTED]");
    }

    @Test
    void masksTokenInWebSocketUri() {
        assertThat(LogSanitizer.sanitize("uri=/ws/chat/12?token=abc123&x=1"))
                .isEqualTo("uri=/ws/chat/12?token=[REDACTED]&x=1");
    }

    @Test
    void masksSecretKeyValuePairsInJsonAndToString() {
        assertThat(LogSanitizer.sanitize("{\"secretKey\":\"f40abc\",\"apiKey\":\"d66xyz\",\"amount\":\"302.50\"}"))
                .isEqualTo("{\"secretKey\":\"[REDACTED]\",\"apiKey\":\"[REDACTED]\",\"amount\":\"302.50\"}");
        assertThat(LogSanitizer.sanitize("User(password=hunter2, name=Asha)"))
                .isEqualTo("User(password=[REDACTED], name=Asha)");
        assertThat(LogSanitizer.sanitize("otp=123456 deviceToken=fcm:abc X-Internal-Token: deadbeef"))
                .isEqualTo("otp=[REDACTED] deviceToken=[REDACTED] X-Internal-Token: [REDACTED]");
    }

    @Test
    void masksEmailsAndIndianMobiles() {
        assertThat(LogSanitizer.sanitize("Failed to send OTP email to asha.k@example.com"))
                .isEqualTo("Failed to send OTP email to a***@example.com");
        assertThat(LogSanitizer.sanitize("User registered: 9876543210")).isEqualTo("User registered: 98******10");
        assertThat(LogSanitizer.sanitize("mobile +91-9876543210")).isEqualTo("mobile 98******10");
    }

    @Test
    void leavesOrdinaryOperationalDataAlone() {
        String line = "Order 1042 total 302.50 status PAID rrn 123456789012 at 1727258412345 pinCode=411001 ORD-5F3A2B1C";
        assertThat(LogSanitizer.sanitize(line)).isEqualTo(line);
        assertThat(LogSanitizer.sanitize(null)).isNull();
    }
}
