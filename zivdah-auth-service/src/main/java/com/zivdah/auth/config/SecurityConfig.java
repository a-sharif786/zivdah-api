package com.zivdah.auth.config;

import com.zivdah.auth.security.JwtAuthenticationFilter;
import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.common.security.InternalAuth;
import com.zivdah.common.security.InternalServiceAuthenticationFilter;
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
                        .pathMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**",
                                "/restful/v1/api/auth/register",
                                "/restful/v1/api/auth/login",
                                "/restful/v1/api/auth/send-otp",
                                "/restful/v1/api/auth/verify-otp",
                                // called precisely when the access token has expired —
                                // authenticated by the refresh token in the body instead
                                "/restful/v1/api/auth/refresh-token",
                                // authenticated in the controller: JWT if still valid, else
                                // the refresh token in the body (see AuthController#logout)
                                "/restful/v1/api/auth/logout",
                                "/restful/v1/api/auth/forget-password",
                                "/restful/v1/api/auth/verify-registration-otp",
                                "/restful/v1/api/auth/reset-password",
                                // authenticated by device secret + PIN in the body (MpinServiceImpl)
                                "/restful/v1/api/auth/mpin/login"
                                // (Removed from this list: deactivate/*, activate/*, all-users. They
                                // are user/admin actions and now require a login; deactivate/* in
                                // particular ran with NO login at all, because its owner-or-admin
                                // check silently passed on an empty security context.)
                        ).permitAll()
                        // Mobile app version check runs at app launch, before login. GET only —
                        // creating/updating/toggling releases stays admin-only (AppVersionController).
                        .pathMatchers(HttpMethod.GET,
                                "/restful/v1/api/auth/app-versions/check",
                                "/restful/v1/api/auth/app-versions/latest"
                        ).permitAll()
                        // Internal, service-to-service only — order-service (invoice customer info),
                        // payment-service (vendor bank details for payouts), notification-service and
                        // chat-service (admin ids, device tokens). These were permitAll(), and the public
                        // api-gateway routes all of /auth/**, so anyone could read any user's name,
                        // email, mobile, bank account, IFSC and UPI id by sequential id. Now they need
                        // the shared internal token (see InternalServiceAuthenticationFilter).
                        .pathMatchers("/restful/v1/api/auth/internal/**").hasRole(InternalAuth.ROLE)
                        .anyExchange().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalApiToken), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
