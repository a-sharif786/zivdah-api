package com.zivdah.payment.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import com.zivdah.common.security.InternalAuth;
import com.zivdah.common.security.InternalServiceAuthenticationFilter;
import com.zivdah.payment.security.JwtAuthenticationFilter;
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
                        // Pushed by EcomWorldPay itself, with no credentials — must stay open, which is
                        // exactly why neither callback's body is trusted any more: both only trigger a
                        // pull of the real status from EcomWorldPay's authenticated status APIs (see
                        // PaymentServiceImpl#handleGatewayCallback / VendorPayoutServiceImpl#handlePayoutCallback).
                        .pathMatchers(HttpMethod.POST, "/restful/v1/api/payments/callback/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/restful/v1/api/payments/payouts/callback").permitAll()
                        // Internal, order-service-only full refund — was permitAll(), i.e. anyone on the
                        // internet could refund any order through the public api-gateway. Now needs the
                        // shared internal token (see InternalServiceAuthenticationFilter).
                        .pathMatchers(HttpMethod.PUT, "/restful/v1/api/payments/order/*/refund").hasRole(InternalAuth.ROLE)
                        // Everything else needs an authenticated caller; role/ownership is then checked in
                        // PaymentController/PaymentServiceImpl. The old "/payments/*" permitAll() made
                        // POST /initiate and GET /{paymentId} (userId, transactionId, UPI intent, payer VPA,
                        // RRN) public for any sequential payment id.
                        .anyExchange().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalApiToken), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
