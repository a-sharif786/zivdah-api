package com.zivdah.product.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.common.security.InternalAuth;
import com.zivdah.common.security.InternalServiceAuthenticationFilter;
import com.zivdah.product.security.JwtAuthenticationFilter;
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
                        // wishlist is per-user data - must stay authenticated even though it's
                        // a single path segment like the public product-browsing endpoints below
                        .pathMatchers(HttpMethod.GET, "/restful/v1/api/products/wishlist").authenticated()
                        .pathMatchers(HttpMethod.GET,
                                "/restful/v1/api/products/getAll",
                                "/restful/v1/api/products/categories",
                                "/restful/v1/api/products/search",
                                "/restful/v1/api/products/category/**",
                                "/restful/v1/api/products/*",
                                "/restful/v1/api/banner/getAll",
                                "/restful/v1/api/category/getAll",
                                // Also covers GET /category/{id}; GET /category/all stays gated
                                // by @PreAuthorize("hasRole('ADMIN')") on the controller method,
                                // same pattern as the /products/* wildcard above.
                                "/restful/v1/api/category/*",
                                // Stored product/banner images — only mapped when
                                // media.serve-locally=true (dev); prod serves them from nginx.
                                "/media/products/**",
                                "/media/banners/**"
                        ).permitAll()
                        // Internal, inventory-service-only push (see ProductServiceClient in
                        // zivdah-inventory-service). Was permitAll(): anyone could set any product's
                        // stock through the public api-gateway. Now needs the internal service token.
                        .pathMatchers(HttpMethod.PUT, "/restful/v1/api/products/*/sync-stock").hasRole(InternalAuth.ROLE)
                        .anyExchange().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalApiToken), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
