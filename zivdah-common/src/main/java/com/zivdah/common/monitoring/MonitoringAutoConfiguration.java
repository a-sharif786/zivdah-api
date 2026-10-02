package com.zivdah.common.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Wires {@link MonitoringBasicAuthWebFilter} into every reactive service and the api-gateway
 * (registered in META-INF/spring/...AutoConfiguration.imports, so no service has to opt in).
 * The servlet eureka-server is skipped — it already has its own HTTP Basic (EUREKA_USERNAME/PASSWORD).
 *
 * <p>Credentials come from MONITOR_USERNAME / MONITOR_PASSWORD (.env → docker-compose env_file).
 * Under the prod profile a missing or short password fails startup, same as INTERNAL_API_TOKEN;
 * anywhere else it falls back to a fixed dev-only pair so IntelliJ runs work without setup.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class MonitoringAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitoringAutoConfiguration.class);

    private static final int MIN_PROD_PASSWORD_LENGTH = 16;
    static final String DEV_USERNAME = "monitor";
    static final String DEV_PASSWORD = "dev-only-monitor-password";

    @Bean
    public MonitoringBasicAuthWebFilter monitoringBasicAuthWebFilter(Environment env) {
        String username = env.getProperty("MONITOR_USERNAME", "");
        String password = env.getProperty("MONITOR_PASSWORD", "");
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));

        if (prod) {
            if (username.isBlank() || username.contains(":") || password.length() < MIN_PROD_PASSWORD_LENGTH) {
                throw new IllegalStateException("MONITOR_USERNAME (no ':') and MONITOR_PASSWORD (at least "
                        + MIN_PROD_PASSWORD_LENGTH + " characters) must be set for the prod profile");
            }
        } else if (username.isBlank() || password.isBlank()) {
            log.warn("MONITOR_USERNAME/MONITOR_PASSWORD not set — using the dev-only login '{}' for /actuator and API docs",
                    DEV_USERNAME);
            username = DEV_USERNAME;
            password = DEV_PASSWORD;
        }
        return new MonitoringBasicAuthWebFilter(username, password);
    }

    /**
     * Services with Spring Security end their JWT chain in anyExchange().authenticated(), which
     * would reject these paths after the Basic filter has already let them through. This chain
     * claims the same paths first and permits them — the Basic filter is the gate.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.config.web.server.ServerHttpSecurity")
    static class SecurityChainConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public SecurityWebFilterChain monitoringSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                            MonitoringBasicAuthWebFilter.PROTECTED_PATHS.toArray(String[]::new)))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                    .authorizeExchange(auth -> auth.anyExchange().permitAll())
                    .build();
        }
    }
}
