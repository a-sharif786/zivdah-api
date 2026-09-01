package com.zivdah.order.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.order.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // explicit: must be evaluated before the single-segment wildcard below, since
                        // "all" would otherwise also match "/orders/*" the same way "/orders/{orderId}" does
                        .pathMatchers(HttpMethod.GET, "/restful/v1/api/orders/all").authenticated()
                        .pathMatchers(HttpMethod.GET, "/restful/v1/api/orders/stats").authenticated()
                        .pathMatchers("/restful/v1/api/orders/*").permitAll()
                        // internal, payment-service-only transition — no user JWT available for this call
                        // (see OrderController#updatePaymentStatus); the status value itself is restricted
                        // server-side to PAID/CANCELLED
                        .pathMatchers(HttpMethod.PUT, "/restful/v1/api/orders/*/payment-status").permitAll()
                        // internal, delivery-service-only sync — no user JWT available for this call
                        // (see OrderController#syncDeliveryStatus)
                        .pathMatchers(HttpMethod.PUT, "/restful/v1/api/orders/*/delivery-status").permitAll()

                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
