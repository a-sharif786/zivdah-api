package com.zivdah.order.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.common.security.InternalAuth;
import com.zivdah.common.security.InternalServiceAuthenticationFilter;
import com.zivdah.order.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, @Value("${internal.api-token}") String internalApiToken) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        // Internal, service-to-service only (payment-service / delivery-service) — these
                        // used to be permitAll(), which (since the public api-gateway routes all of
                        // /orders/**) let anyone on the internet mark any order PAID or DELIVERED.
                        // Now they need the shared internal token (see InternalServiceAuthenticationFilter).
                        .pathMatchers(HttpMethod.PUT,
                                "/restful/v1/api/orders/*/payment-status",
                                "/restful/v1/api/orders/*/delivery-status").hasRole(InternalAuth.ROLE)
                        // Everything else — create, GET /{orderId}, /user/{userId}, cancel, invoices —
                        // needs an authenticated caller (a user JWT or, for the internal GET /{orderId}
                        // lookups other services make, the internal token), with ownership then checked
                        // in OrderController/InvoiceController itself. The old "/orders/*" permitAll()
                        // exposed create and GET /{orderId} (full delivery address) to anyone.
                        .anyExchange().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalApiToken), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
