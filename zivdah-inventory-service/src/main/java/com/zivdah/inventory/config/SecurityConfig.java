package com.zivdah.inventory.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.common.security.InternalAuth;
import com.zivdah.common.security.InternalServiceAuthenticationFilter;
import com.zivdah.inventory.security.JwtAuthenticationFilter;
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
                        // Internal, product-service-only push (see InventoryServiceClient in
                        // zivdah-product-service). Was permitAll(): anyone could set any product's
                        // available quantity through the public api-gateway. Now needs the internal token.
                        .pathMatchers(HttpMethod.PUT, "/restful/v1/api/inventory/*/sync-quantity").hasRole(InternalAuth.ROLE)
                        .anyExchange().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalApiToken), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
