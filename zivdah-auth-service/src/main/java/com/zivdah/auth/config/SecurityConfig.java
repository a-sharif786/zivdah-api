package com.zivdah.auth.config;

import com.zivdah.auth.security.JwtAuthenticationFilter;
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
                        .pathMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**",
                                "/restful/v1/api/auth/register",
                                "/restful/v1/api/auth/login",
                                "/restful/v1/api/auth/send-otp",
                                "/restful/v1/api/auth/verify-otp",
                                "/restful/v1/api/auth/forget-password",
                                "/restful/v1/api/auth/verify-registration-otp",
                                "/restful/v1/api/auth/reset-password",
                                "/restful/v1/api/auth/deactivate/*",
                                "/restful/v1/api/auth/activate/*",
                                "/restful/v1/api/auth/activate/*",
                                "/restful/v1/api/auth/all-users",
                                // internal, notification-service-only fan-out lookup — no
                                // user JWT available for that call (see AuthController). The
                                // single-segment wildcard covers both /internal/device-tokens/{userId}
                                // (GET) and /internal/device-tokens/deactivate (PATCH).
                                "/restful/v1/api/auth/internal/admin-ids",
                                "/restful/v1/api/auth/internal/device-tokens/*"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
