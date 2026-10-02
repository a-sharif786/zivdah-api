package com.zivdah.payment.controller;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.payment.config.SecurityConfig;
import com.zivdah.payment.dto.PaymentResponseDto;
import com.zivdah.payment.enums.PaymentStatus;
import com.zivdah.payment.security.JwtAuthenticationFilter;
import com.zivdah.payment.security.JwtTokenProvider;
import com.zivdah.payment.service.PaymentService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// HTTP-level check of payment-service's endpoint protection with the real SecurityConfig, JWT
// filter, internal-token filter and @PreAuthorize rules wired in (only PaymentService is mocked).
@WebFluxTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=" + PaymentControllerSecurityTest.JWT_SECRET,
        "internal.api-token=" + PaymentControllerSecurityTest.INTERNAL_TOKEN
})
class PaymentControllerSecurityTest {

    static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long";
    static final String INTERNAL_TOKEN = "test-internal-token-that-is-at-least-32-chars";

    private static final String BASE = "/restful/v1/api/payments";

    @Autowired private WebTestClient client;
    @MockitoBean private PaymentService paymentService;

    private final PaymentResponseDto payment = PaymentResponseDto.builder()
            .paymentId(11L).userId(7L).status(PaymentStatus.SUCCESS).build();

    @BeforeEach
    void setUp() {
        when(paymentService.markPaymentSuccess(anyLong())).thenReturn(Mono.just(payment));
        when(paymentService.markPaymentFailed(anyLong())).thenReturn(Mono.just(payment));
        when(paymentService.refundByOrder(anyLong())).thenReturn(Mono.empty());
        when(paymentService.getPayment(anyLong(), any(), anyBoolean())).thenReturn(Mono.just(payment));
        when(paymentService.getPaymentsByOrder(anyLong(), any(), anyBoolean())).thenReturn(Flux.just(payment));
        when(paymentService.initiatePayment(any(), anyLong())).thenReturn(Mono.just(payment));
        when(paymentService.linkOrder(anyLong(), anyLong(), anyLong())).thenReturn(Mono.just(payment));
        when(paymentService.handleGatewayCallback(any())).thenReturn(Mono.empty());
    }

    private static String jwt(long userId, String role) {
        return Jwts.builder().setSubject("9999999999").claim("userId", userId).claim("role", role)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- P0-3: manual success/failed are ADMIN-only ------------------------------------------
    // A @PreAuthorize denial surfaces as 400 {"message":"Access Denied"}, not 403: every service's
    // GlobalExceptionHandler maps RuntimeException (incl. AccessDeniedException) to 400, and the
    // Flutter app's AuthFailureInterceptor relies on exactly that shape — left as-is here (audit
    // item P1-7). What matters is that the service method is never reached.

    @Test
    void customerCannotMarkAPaymentSuccessful() {
        client.put().uri(BASE + "/success/11").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Access Denied");
        verify(paymentService, never()).markPaymentSuccess(anyLong());
    }

    @Test
    void vendorCannotMarkAPaymentSuccessful() {
        client.put().uri(BASE + "/success/11").headers(h -> h.setBearerAuth(jwt(50L, "VENDOR")))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Access Denied");
    }

    @Test
    void anonymousCannotMarkAPaymentSuccessful() {
        client.put().uri(BASE + "/success/11").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void adminCanMarkPaymentSuccessOrFailed() {
        client.put().uri(BASE + "/success/11").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isOk();
        client.put().uri(BASE + "/failed/11").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isOk();
        verify(paymentService).markPaymentSuccess(11L);
        verify(paymentService).markPaymentFailed(11L);
    }

    @Test
    void customerCannotMarkAPaymentFailed() {
        client.put().uri(BASE + "/failed/11").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Access Denied");
    }

    // --- P0-3/P0-4: order refund is internal-only ---------------------------------------------

    @Test
    void anonymousCannotRefundAnOrder() {
        client.put().uri(BASE + "/order/100/refund").exchange().expectStatus().isUnauthorized();
        verify(paymentService, never()).refundByOrder(anyLong());
    }

    @Test
    void evenAdminJwtCannotUseTheInternalRefundEndpoint() {
        client.put().uri(BASE + "/order/100/refund").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isForbidden();
        verify(paymentService, never()).refundByOrder(anyLong());
    }

    @Test
    void internalServiceCanRefundAnOrder() {
        client.put().uri(BASE + "/order/100/refund").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk();
        verify(paymentService).refundByOrder(100L);
    }

    // --- reads / initiate need a login; identity comes from the JWT ---------------------------

    @Test
    void anonymousCannotReadAPayment() {
        client.get().uri(BASE + "/11").exchange().expectStatus().isUnauthorized();
        verify(paymentService, never()).getPayment(anyLong(), any(), anyBoolean());
    }

    @Test
    void anonymousCannotInitiateAPayment() {
        client.post().uri(BASE + "/initiate").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
        verify(paymentService, never()).initiatePayment(any(), anyLong());
    }

    @Test
    void customerReadIsScopedToTheirOwnIdentity() {
        client.get().uri(BASE + "/11").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isOk();
        verify(paymentService).getPayment(11L, 7L, false);
    }

    @Test
    void adminReadIsPrivileged() {
        client.get().uri(BASE + "/11").headers(h -> h.setBearerAuth(jwt(1L, "ADMIN")))
                .exchange().expectStatus().isOk();
        verify(paymentService).getPayment(11L, 1L, true);
    }

    @Test
    void internalServiceCanListAnOrdersPayments() {
        client.get().uri(BASE + "/order/100").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk();
        verify(paymentService).getPaymentsByOrder(100L, null, true);
    }

    @Test
    void initiateAndLinkUseTheJwtUserNotTheBody() {
        client.post().uri(BASE + "/initiate").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"userId\":8,\"checkoutRef\":\"c\"}")
                .exchange().expectStatus().isOk();
        verify(paymentService).initiatePayment(any(), eq(7L));
        client.put().uri(BASE + "/11/link-order").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"orderId\":100}")
                .exchange().expectStatus().isOk();
        verify(paymentService).linkOrder(11L, 100L, 7L);
    }

    // --- the gateway callback stays reachable (its body is untrusted server-side) -------------

    @Test
    void gatewayCallbackIsReachableWithoutCredentials() {
        client.post().uri(BASE + "/callback/ecomworldpay").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"invoiceNumber\":\"x\",\"status\":\"SUCCESS\"}")
                .exchange().expectStatus().isOk();
        verify(paymentService).handleGatewayCallback(any());
    }
}
