package com.zivdah.product.config;

import com.zivdah.product.security.JwtAuthenticationFilter;
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
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
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
                                "/restful/v1/api/category/*"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
