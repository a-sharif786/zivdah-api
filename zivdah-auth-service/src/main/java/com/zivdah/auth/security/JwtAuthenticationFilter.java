package com.zivdah.auth.security;

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

        log.info("=================================================");
        log.info("JWT FILTER EXECUTED");
        log.info("Authorization Header : {}", header);

        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("Bearer token not found.");
            return chain.filter(exchange);
        }

        String token = header.substring(7);

        boolean valid = jwtTokenProvider.validateToken(token);

        log.info("Token Valid : {}", valid);

        if (!valid) {
            return chain.filter(exchange);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String mobile = jwtTokenProvider.getMobileNumberFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);

        log.info("UserId : {}", userId);
        log.info("Mobile : {}", mobile);
        log.info("Role : {}", role);

        String principal = userId != null ? userId.toString() : mobile;

        List<SimpleGrantedAuthority> authorities =
                role == null
                        ? Collections.emptyList()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

        log.info("Authorities : {}", authorities);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                );
    }
}
//package com.zivdah.auth.security;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.ReactiveSecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.web.server.WebFilter;
//import org.springframework.web.server.WebFilterChain;
//import reactor.core.publisher.Mono;
//
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//public class JwtAuthenticationFilter implements WebFilter {
//
//    private final JwtTokenProvider jwtTokenProvider;
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
//        if (header != null && header.startsWith("Bearer ")) {
//            String token = header.substring(7);
//            if (jwtTokenProvider.validateToken(token)) {
//                Long userId = jwtTokenProvider.getUserIdFromToken(token);
//                String mobile = jwtTokenProvider.getMobileNumberFromToken(token);
//                String role = jwtTokenProvider.getRoleFromToken(token);
//                String principal = userId != null ? userId.toString() : mobile;
//                List<SimpleGrantedAuthority> authorities = role == null
//                        ? List.of()
//                        : List.of(new SimpleGrantedAuthority("ROLE_" + role));
//                return chain.filter(exchange)
//                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
//                                new UsernamePasswordAuthenticationToken(principal, null, authorities)
//                        ));
//            }
//        }
//        return chain.filter(exchange);
//    }
//}
