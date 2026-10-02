package com.zivdah.logserver.security;

import lombok.RequiredArgsConstructor;
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

// Same bearer-token handling as every other service's JwtAuthenticationFilter: a missing/invalid
// token leaves the request unauthenticated and SecurityConfig decides. Deliberately logs nothing
// about the token — this service's own log lines end up in the very store it protects.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return chain.filter(exchange);
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);
        List<SimpleGrantedAuthority> authorities = role == null
                ? Collections.emptyList()
                : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities)));
    }
}
