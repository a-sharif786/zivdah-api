package com.zivdah.auth.controller;

import com.zivdah.auth.config.SecurityConfig;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.security.JwtAuthenticationFilter;
import com.zivdah.auth.security.JwtTokenProvider;
import com.zivdah.auth.service.AuthService;
import com.zivdah.common.security.InternalAuth;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// HTTP-level check of auth-service's internal endpoints and the deactivate fix, with the real
// SecurityConfig, JWT filter and internal-token filter (only AuthService is mocked).
@WebFluxTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=" + AuthControllerSecurityTest.JWT_SECRET,
        "internal.api-token=" + AuthControllerSecurityTest.INTERNAL_TOKEN
})
class AuthControllerSecurityTest {

    static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long";
    static final String INTERNAL_TOKEN = "test-internal-token-that-is-at-least-32-chars";

    private static final String BASE = "/restful/v1/api/auth";

    @Autowired private WebTestClient client;
    @MockitoBean private AuthService authService;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setName("Asha");
        when(authService.getUserById(anyLong())).thenReturn(Mono.just(user));
        when(authService.getAdminUserIds()).thenReturn(Flux.just(1L));
        when(authService.getActiveDeviceTokens(anyLong())).thenReturn(Flux.empty());
        when(authService.deactivateAccount(anyLong())).thenReturn(Mono.empty());
    }

    private static String jwt(long userId, String role) {
        return Jwts.builder().setSubject("9999999999").claim("userId", userId).claim("role", role)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- /internal/** (bank details, PII, admin ids, device tokens) ---------------------------

    @Test
    void internalUserLookupIsNotPublic() {
        client.get().uri(BASE + "/internal/users/7").exchange().expectStatus().isUnauthorized();
        verify(authService, never()).getUserById(anyLong());
    }

    @Test
    void internalUserLookupRejectsAUserJwtEvenForTheirOwnId() {
        client.get().uri(BASE + "/internal/users/7").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isForbidden();
        verify(authService, never()).getUserById(anyLong());
    }

    @Test
    void internalUserLookupWorksWithTheInternalToken() {
        client.get().uri(BASE + "/internal/users/7").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.id").isEqualTo(7);
    }

    @Test
    void adminIdsAndDeviceTokensAreInternalOnly() {
        client.get().uri(BASE + "/internal/admin-ids").exchange().expectStatus().isUnauthorized();
        client.get().uri(BASE + "/internal/device-tokens/7").exchange().expectStatus().isUnauthorized();
        client.get().uri(BASE + "/internal/admin-ids").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk();
        client.get().uri(BASE + "/internal/device-tokens/7").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk();
    }

    // --- deactivate ------------------------------------------------------------------------

    @Test
    void deactivateWithoutLoginIsRejected() {
        client.put().uri(BASE + "/deactivate/7").exchange().expectStatus().isUnauthorized();
        verify(authService, never()).deactivateAccount(anyLong());
    }

    @Test
    void userCannotDeactivateSomeoneElse() {
        client.put().uri(BASE + "/deactivate/7").headers(h -> h.setBearerAuth(jwt(8L, "CUSTOMER")))
                .exchange().expectStatus().isForbidden();
        verify(authService, never()).deactivateAccount(anyLong());
    }

    @Test
    void userCanDeactivateOwnAccountAndAdminAnyAccount() {
        client.put().uri(BASE + "/deactivate/7").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isOk();
        client.put().uri(BASE + "/deactivate/9").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isOk();
        verify(authService).deactivateAccount(7L);
        verify(authService).deactivateAccount(9L);
    }
}
