package com.zivdah.auth.controller;

import com.zivdah.auth.config.SecurityConfig;
import com.zivdah.auth.dto.LoginResponseDTO;
import com.zivdah.auth.dto.MpinSetupResponseDTO;
import com.zivdah.auth.security.JwtAuthenticationFilter;
import com.zivdah.auth.security.JwtTokenProvider;
import com.zivdah.auth.service.MpinService;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Real SecurityConfig + JWT filter; only MpinService is mocked. Checks which MPIN endpoints are
// public, and that /setup hands the service the userId and auth_time from the caller's JWT.
@WebFluxTest(MpinController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=" + MpinControllerSecurityTest.JWT_SECRET,
        "internal.api-token=" + MpinControllerSecurityTest.INTERNAL_TOKEN
})
class MpinControllerSecurityTest {

    static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long";
    static final String INTERNAL_TOKEN = "test-internal-token-that-is-at-least-32-chars";

    private static final String BASE = "/restful/v1/api/auth/mpin";
    private static final String SETUP_BODY = "{\"deviceId\":\"device-0001\",\"mpin\":\"482917\"}";

    @Autowired private WebTestClient client;
    @MockitoBean private MpinService mpinService;

    private static String jwt(long userId, Instant authTime) {
        JwtBuilder b = Jwts.builder().setSubject("9876543210").claim("userId", userId).claim("role", "USER")
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000));
        if (authTime != null) {
            b.claim("auth_time", authTime.getEpochSecond());
        }
        return b.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    @Test
    void loginIsPublic() {
        when(mpinService.login(any())).thenReturn(Mono.just(LoginResponseDTO.builder().id(7L).token("t").build()));

        client.post().uri(BASE + "/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mobile\":\"9876543210\",\"deviceId\":\"device-0001\",\"deviceSecret\":\"s\",\"mpin\":\"482917\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.token").isEqualTo("t");
    }

    @Test
    void everythingElseNeedsALogin() {
        client.post().uri(BASE + "/setup").contentType(MediaType.APPLICATION_JSON).bodyValue(SETUP_BODY)
                .exchange().expectStatus().isUnauthorized();
        client.get().uri(BASE + "/devices").exchange().expectStatus().isUnauthorized();
        client.delete().uri(BASE + "/devices/device-0001").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(mpinService);
    }

    @Test
    void setupPassesUserIdAndAuthTimeFromTheJwt() {
        Instant authTime = Instant.ofEpochSecond(Instant.now().getEpochSecond() - 60);
        when(mpinService.setup(eq(7L), eq(authTime), any()))
                .thenReturn(Mono.just(MpinSetupResponseDTO.builder().deviceId("device-0001").deviceSecret("sec").maxAttempts(5).build()));

        client.post().uri(BASE + "/setup").contentType(MediaType.APPLICATION_JSON).bodyValue(SETUP_BODY)
                .headers(h -> h.setBearerAuth(jwt(7L, authTime)))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.data.deviceSecret").isEqualTo("sec");
    }

    @Test
    void setupRejectsAMalformedPinBeforeReachingTheService() {
        client.post().uri(BASE + "/setup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"deviceId\":\"device-0001\",\"mpin\":\"12ab\"}")
                .headers(h -> h.setBearerAuth(jwt(7L, Instant.now())))
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(mpinService);
    }
}
