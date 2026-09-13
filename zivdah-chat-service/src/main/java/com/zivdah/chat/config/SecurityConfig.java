package com.zivdah.chat.config;

import com.zivdah.chat.security.JwtAuthenticationFilter;
import com.zivdah.common.logging.CorrelationIdWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorrelationIdWebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        // A WebSocket handshake can't carry an Authorization header — the JWT arrives as
                        // a `token` query param instead and is validated as the very first thing inside
                        // ChatWebSocketHandler#handle() itself (closing with POLICY_VIOLATION on
                        // failure), not through this filter chain. This permitAll only lets the
                        // handshake request through to that handler; it is not a real auth bypass.
                        .pathMatchers("/ws/chat/**").permitAll()
                        // Every chat endpoint is behind an authenticated JWT — granular role/ownership
                        // checks (USER vs ADMIN, and conversation.customerId == caller) live at the
                        // controller/service layer via @PreAuthorize + explicit ownership checks, same
                        // split as zivdah-order-service's SecurityConfig.
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
