package com.zivdah.common.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringBasicAuthWebFilterTest {

    private final MonitoringBasicAuthWebFilter filter = new MonitoringBasicAuthWebFilter("monitor", "s3cret-password");

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    /** Runs the filter; returns whether the request reached the rest of the chain. */
    private boolean passes(MockServerWebExchange exchange) {
        AtomicBoolean reached = new AtomicBoolean();
        WebFilterChain chain = ex -> { reached.set(true); return Mono.empty(); };
        filter.filter(exchange, chain).block();
        return reached.get();
    }

    @Test
    void protectedPathWithoutCredentialsGetsBasicChallenge() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/info"));
        assertThat(passes(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Basic realm=\"Zivdah Monitoring\"");
    }

    @Test
    void correctCredentialsPass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v3/api-docs")
                .header(HttpHeaders.AUTHORIZATION, basic("monitor", "s3cret-password")));
        assertThat(passes(exchange)).isTrue();
    }

    @Test
    void wrongPasswordOrGarbageIsRejected() {
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, basic("monitor", "wrong"))))).isFalse();
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Basic !!!not-base64")))).isFalse();
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token")))).isFalse();
    }

    @Test
    void healthAndNormalApiPathsAreUntouched() {
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health")))).isTrue();
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health/liveness")))).isTrue();
        assertThat(passes(MockServerWebExchange.from(MockServerHttpRequest.get("/restful/v1/api/payments/1")))).isTrue();
    }

    @Test
    void swaggerUiAndGatewayDocRoutesAreProtected() {
        assertThat(MonitoringBasicAuthWebFilter.requiresAuth("/swagger-ui.html")).isTrue();
        assertThat(MonitoringBasicAuthWebFilter.requiresAuth("/webjars/swagger-ui/index.html")).isTrue();
        assertThat(MonitoringBasicAuthWebFilter.requiresAuth("/v3/api-docs/payments")).isTrue();
        assertThat(MonitoringBasicAuthWebFilter.requiresAuth("/actuator")).isTrue();
    }
}
