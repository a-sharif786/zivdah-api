package com.zivdah.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class InternalServiceAuthenticationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthenticationFilter.class);

    // 32 chars is the floor for a randomly generated token (e.g. `openssl rand -hex 32` gives 64).
    private static final int MIN_TOKEN_LENGTH = 32;

    private final byte[] expectedToken;

    public InternalServiceAuthenticationFilter(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank() || expectedToken.length() < MIN_TOKEN_LENGTH) {
            // Fail at startup, not at the first internal call: a missing/weak token would
            // otherwise either break every service-to-service call or be trivially guessable.
            throw new IllegalStateException(
                    "internal.api-token (INTERNAL_API_TOKEN) must be set to a random value of at least "
                            + MIN_TOKEN_LENGTH + " characters");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String presented = exchange.getRequest().getHeaders().getFirst(InternalAuth.HEADER);
        if (presented == null) {
            return chain.filter(exchange);
        }
        // Constant-time compare — a plain equals() leaks how many leading characters matched.
        if (!MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected internal-service token on {} {}",
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                InternalAuth.PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_" + InternalAuth.ROLE)));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
