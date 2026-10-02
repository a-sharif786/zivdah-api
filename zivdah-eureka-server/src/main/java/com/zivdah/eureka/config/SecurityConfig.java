package com.zivdah.eureka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Protects the Eureka registry and dashboard with HTTP Basic when {@code zivdah.eureka.auth.enabled}
 * is true (application-prod.yaml). Every client then registers through a defaultZone that carries
 * the credentials — {@code EUREKA_URL=http://<user>:<password>@eureka-server:8761/eureka} in .env,
 * which docker-compose.yml hands to every service as EUREKA_CLIENT_SERVICEURL_DEFAULTZONE.
 *
 * <p>Off by default (dev): the IntelliJ-run services register against http://localhost:8761/eureka
 * without credentials, and requiring them there would just break local runs.
 *
 * <p>CSRF is disabled for /eureka/** because Eureka clients register/renew with plain
 * POST/PUT/DELETE calls that never carry a CSRF token; they authenticate with Basic instead.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain eurekaSecurity(
            HttpSecurity http, @Value("${zivdah.eureka.auth.enabled:false}") boolean authEnabled) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"));
        if (authEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .formLogin(form -> form.disable());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
