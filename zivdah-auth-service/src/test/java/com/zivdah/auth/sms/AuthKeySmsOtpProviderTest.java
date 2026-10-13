package com.zivdah.auth.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zivdah.auth.exception.OtpException;
import com.zivdah.auth.sms.SmsOtpProvider.OtpCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs the real WebClient against a local stand-in for console.authkey.io (JDK HttpServer).
class AuthKeySmsOtpProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> nextBody = new AtomicReference<>();
    private AuthKeySmsOtpProvider provider;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            byte[] body = nextBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        provider = new AuthKeySmsOtpProvider(mapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key&with{odd}chars", "1234", "91", Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            q.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return q;
    }

    @Test
    void sendCallsRequestPhpAndReturnsTheLogId() {
        nextBody.set("{\"LogID\":\"28bf7375bb54540ba03a4eb873d4da44\",\"Message\":\"Submitted Successfully\"}");

        StepVerifier.create(provider.send("+91 98765-43210"))
                .expectNext("28bf7375bb54540ba03a4eb873d4da44")
                .verifyComplete();

        assertThat(lastPath.get()).isEqualTo("/restapi/request.php");
        assertThat(lastQuery.get())
                .containsEntry("authkey", "key&with{odd}chars")   // percent-encoded on the wire, intact here
                .containsEntry("mobile", "9876543210")
                .containsEntry("country_code", "91")
                .containsEntry("sid", "1234");
    }

    @Test
    void verifyCalls2faVerifyWithTheLogId() {
        nextBody.set("{\"status\":true,\"message\":\"Valid OTP\"}");

        StepVerifier.create(provider.verify("9876543210", "28bf7375bb54540ba03a4eb873d4da44", "482917"))
                .expectNext(OtpCheckResult.VALID)
                .verifyComplete();

        assertThat(lastPath.get()).isEqualTo("/api/2fa_verify.php");
        assertThat(lastQuery.get())
                .containsEntry("channel", "SMS")
                .containsEntry("logid", "28bf7375bb54540ba03a4eb873d4da44")
                .containsEntry("otp", "482917");
    }

    @Test
    void sendWithoutLogIdIs503() {
        nextBody.set("{\"Message\":\"Invalid Authkey\"}");

        StepVerifier.create(provider.send("9876543210"))
                .verifyErrorSatisfies(e -> {
                    assertThat(((OtpException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(AuthKeySmsOtpProvider.UNAVAILABLE);
                });
    }

    @Test
    void unreachableProviderIs503() {
        server.stop(0);

        StepVerifier.create(provider.send("9876543210"))
                .verifyErrorSatisfies(e -> assertThat(((OtpException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void verifyFailuresMapToResults() throws Exception {
        assertThat(AuthKeySmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":false,\"message\":\"Invalid OTP\"}")))
                .isEqualTo(OtpCheckResult.INVALID);
        assertThat(AuthKeySmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":false,\"message\":\"OTP Expired\"}")))
                .isEqualTo(OtpCheckResult.EXPIRED);
        assertThatThrownBy(() -> AuthKeySmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":false,\"message\":\"Invalid Authkey\"}")))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void mobileIsSentWithoutCountryCode() {
        assertThat(AuthKeySmsOtpProvider.localNumber("919876543210", "91")).isEqualTo("9876543210");
        assertThat(AuthKeySmsOtpProvider.localNumber("09876543210", "91")).isEqualTo("9876543210");
        assertThat(AuthKeySmsOtpProvider.localNumber("9176543210", "91")).isEqualTo("9176543210"); // 10 digits: leave alone
    }

    @Test
    void refusesToStartWithoutCredentials() {
        assertThatThrownBy(() -> new AuthKeySmsOtpProvider(mapper, "http://x", "key", " ", "91", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
