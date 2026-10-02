package com.zivdah.common.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * HTTP Basic in front of the operational endpoints — Actuator, the OpenAPI docs and (on the
 * gateway) Swagger UI — with one shared MONITOR_USERNAME / MONITOR_PASSWORD across every service.
 *
 * <p>A plain WebFilter rather than a Spring Security chain so the same check also runs on the
 * api-gateway, which has no Spring Security. It is ordered ahead of Spring Security's
 * WebFilterChainProxy; in services that do have Spring Security, {@link MonitoringAutoConfiguration}
 * adds a permitAll chain for these same paths so the JWT chain doesn't reject them afterwards.
 *
 * <p>Every service uses the same realm, so after one browser login the gateway's Swagger UI can
 * fetch each service's /v3/api-docs (the gateway forwards the Authorization header) without
 * prompting again.
 */
public class MonitoringBasicAuthWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MonitoringBasicAuthWebFilter.class);

    static final String REALM = "Zivdah Monitoring";

    static final List<String> PROTECTED_PATHS = List.of(
            "/actuator", "/actuator/**",
            "/v3/api-docs", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**",
            "/webjars/**");

    // Liveness only (details are never shown — see MonitoringEnvironmentPostProcessor), so Docker
    // healthchecks and load balancers can probe it without credentials.
    static final List<String> OPEN_PATHS = List.of("/actuator/health", "/actuator/health/**");

    private static final List<PathPattern> PROTECTED = compile(PROTECTED_PATHS);
    private static final List<PathPattern> OPEN = compile(OPEN_PATHS);

    private final byte[] expectedCredentials;

    public MonitoringBasicAuthWebFilter(String username, String password) {
        this.expectedCredentials = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
    }

    private static List<PathPattern> compile(List<String> patterns) {
        PathPatternParser parser = new PathPatternParser();
        return patterns.stream().map(parser::parse).toList();
    }

    static boolean requiresAuth(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return PROTECTED.stream().anyMatch(p -> p.matches(container))
                && OPEN.stream().noneMatch(p -> p.matches(container));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!requiresAuth(path) || isAuthorized(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            log.warn("Rejected monitoring credentials on {} {}", exchange.getRequest().getMethod(), path);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
        return exchange.getResponse().setComplete();
    }

    private boolean isAuthorized(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        byte[] presented;
        try {
            presented = Base64.getDecoder().decode(header.substring(6).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Constant-time compare — a plain equals() leaks how many leading characters matched.
        boolean ok = MessageDigest.isEqual(expectedCredentials, presented);
        Arrays.fill(presented, (byte) 0);
        return ok;
    }

    // Right after CorrelationIdWebFilter (HIGHEST_PRECEDENCE), well before Spring Security (-100)
    // and, on the gateway, before routing.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
