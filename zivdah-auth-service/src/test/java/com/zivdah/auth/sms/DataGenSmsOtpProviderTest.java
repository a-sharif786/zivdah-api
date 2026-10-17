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

// Runs the real WebClient against a local stand-in for global.datagenit.com/API (JDK HttpServer).
class DataGenSmsOtpProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> nextBody = new AtomicReference<>();
    private DataGenSmsOtpProvider provider;

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
        provider = new DataGenSmsOtpProvider(mapper,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/API",
                "key&with{odd}chars", "ZIVDAH", "91", "1201160000000000000", "", Duration.ofSeconds(5));
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
    void sendCallsGenerateOtpAndReturnsTheLogId() {
        nextBody.set("{\"status\":\"success\",\"logid\":\"5feb7b51aca0d\",\"desc\":\"OTP Sent\",\"code\":100,\"ts\":\"2020-12-30 23:41:27\"}");

        StepVerifier.create(provider.send("+91 98765-43210"))
                .expectNext("5feb7b51aca0d")
                .verifyComplete();

        assertThat(lastPath.get()).isEqualTo("/API/generate_otp.php");
        assertThat(lastQuery.get())
                .containsEntry("auth", "key&with{odd}chars")
                .containsEntry("msisdn", "9876543210")
                .containsEntry("senderid", "ZIVDAH")
                .containsEntry("countrycode", "91")
                .containsEntry("entity_id", "1201160000000000000")
                .doesNotContainKey("template_id");
    }

    @Test
    void verifyCallsVerifyOtpWithTheLogId() {
        nextBody.set("{\"status\":\"success\",\"code\":200,\"desc\":\"OTP verified successfully\",\"ts\":\"2020-12-30 23:43:32\"}");

        StepVerifier.create(provider.verify("9876543210", "5feb7b51aca0d", "482917"))
                .expectNext(OtpCheckResult.VALID)
                .verifyComplete();

        assertThat(lastPath.get()).isEqualTo("/API/verify_otp.php");
        assertThat(lastQuery.get())
                .containsEntry("msisdn", "9876543210")
                .containsEntry("logid", "5feb7b51aca0d")
                .containsEntry("otp", "482917");
    }

    @Test
    void sendFailureIs503() {
        nextBody.set("{\"status\":\"failure\",\"code\":413,\"desc\":\"Sender Id Not Approved\",\"ts\":\"2018-11-20 12:56:45\"}");

        StepVerifier.create(provider.send("9876543210"))
                .verifyErrorSatisfies(e -> {
                    assertThat(((OtpException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(DataGenSmsOtpProvider.UNAVAILABLE);
                });
    }

    @Test
    void verifyFailuresMapToResults() throws Exception {
        assertThat(DataGenSmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":\"failure\",\"code\":419,\"desc\":\"invalid otp\"}")))
                .isEqualTo(OtpCheckResult.INVALID);
        assertThat(DataGenSmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":\"failure\",\"code\":421,\"desc\":\"OTP time limit exceeded\"}")))
                .isEqualTo(OtpCheckResult.EXPIRED);
        assertThat(DataGenSmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":\"failure\",\"code\":\"422\",\"desc\":\"OTP already verified for this request\"}")))
                .isEqualTo(OtpCheckResult.EXPIRED);
        assertThatThrownBy(() -> DataGenSmsOtpProvider.interpretVerify(mapper.readTree("{\"status\":\"failure\",\"code\":407,\"desc\":\"Access Denied\"}")))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void refusesToStartWithoutCredentials() {
        assertThatThrownBy(() -> new DataGenSmsOtpProvider(mapper, "http://x", "key", " ", "91", "", "", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
