package com.zivdah.chat.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");

        log.debug("JWT filter executed. Authorization header present: {}", header != null);

        if (header == null || !header.startsWith("Bearer ")) {
            log.debug("Bearer token not found.");
            return chain.filter(exchange);
        }

        String token = header.substring(7);
        boolean valid = jwtTokenProvider.validateToken(token);

        if (!valid) {
            log.debug("JWT token failed validation.");
            return chain.filter(exchange);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String mobile = jwtTokenProvider.getMobileNumberFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);

        // Claim values (userId/mobile/role) are logged at DEBUG only — they're PII and this
        // filter's log lines are now shipped to the centralized zivdah-log-server, so INFO
        // (the default level) must not carry them.
        log.debug("JWT validated — userId={}, mobile={}, role={}", userId, mobile, role);

        String principal = userId != null ? userId.toString() : mobile;
        List<SimpleGrantedAuthority> authorities =
                role == null
                        ? Collections.emptyList()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

        log.info("JWT authentication succeeded");

        // The raw token is carried as the Authentication's credentials (not just discarded as
        // `null`, unlike the servlet-filter services this was copied from) so downstream code —
        // ChatBotController's currentToken() helper — can forward the caller's own JWT to the
        // handful of other services' endpoints that require an authenticated caller (see
        // OrderServiceClient/DeliveryServiceClient's Javadoc).
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, token, authorities)
                ));
    }
}
