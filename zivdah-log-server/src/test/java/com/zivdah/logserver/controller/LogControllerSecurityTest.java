package com.zivdah.logserver.controller;

import com.zivdah.logserver.config.SecurityConfig;
import com.zivdah.logserver.security.JwtAuthenticationFilter;
import com.zivdah.logserver.security.JwtTokenProvider;
import com.zivdah.logserver.service.LogQueryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WebFluxTest(LogController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "jwt.secret=" + LogControllerSecurityTest.JWT_SECRET)
class LogControllerSecurityTest {

    static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long";

    @Autowired private WebTestClient client;
    @MockitoBean private LogQueryService logQueryService;

    @BeforeEach
    void setUp() {
        when(logQueryService.search(any(), any(), any(), any(), any(), any())).thenReturn(Flux.empty());
    }

    private static String jwt(long userId, String role) {
        return Jwts.builder().setSubject("9999999999").claim("userId", userId).claim("role", role)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void anonymousCannotReadLogs() {
        client.get().uri("/restful/v1/api/logs").exchange().expectStatus().isUnauthorized();
        client.get().uri("/restful/v1/api/logs/1").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(logQueryService);
    }

    @Test
    void nonAdminsCannotReadLogs() {
        for (String role : new String[]{"CUSTOMER", "VENDOR", "DELIVERY_BOY"}) {
            client.get().uri("/restful/v1/api/logs").headers(h -> h.setBearerAuth(jwt(7L, role)))
                    .exchange().expectStatus().isForbidden();
        }
        verifyNoInteractions(logQueryService);
    }

    @Test
    void forgedTokenIsRejected() {
        String forged = Jwts.builder().claim("userId", 1).claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("some-other-secret-that-is-32-chars-long!!".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
        client.get().uri("/restful/v1/api/logs").headers(h -> h.setBearerAuth(forged))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void adminCanReadLogsAndPageSizeIsCapped() {
        client.get().uri("/restful/v1/api/logs?size=100000").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isOk();
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(logQueryService).search(any(), any(), any(), any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
    }
}
