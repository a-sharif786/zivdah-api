package com.zivdah.order.controller;

import com.zivdah.common.security.InternalAuth;
import com.zivdah.order.client.DeliveryServiceClient;
import com.zivdah.order.config.SecurityConfig;
import com.zivdah.order.dto.OrderItemDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.enums.OrderStatus;
import com.zivdah.order.security.JwtAuthenticationFilter;
import com.zivdah.order.security.JwtTokenProvider;
import com.zivdah.order.service.OrderService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// HTTP-level check of order-service's endpoint protection with the real SecurityConfig, JWT filter
// and internal-token filter wired in (only OrderService/DeliveryServiceClient are mocked).
@WebFluxTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=" + OrderControllerSecurityTest.JWT_SECRET,
        "internal.api-token=" + OrderControllerSecurityTest.INTERNAL_TOKEN
})
class OrderControllerSecurityTest {

    static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-characters-long";
    static final String INTERNAL_TOKEN = "test-internal-token-that-is-at-least-32-chars";

    private static final String BASE = "/restful/v1/api/orders";

    @Autowired private WebTestClient client;
    @MockitoBean private OrderService orderService;
    @MockitoBean private DeliveryServiceClient deliveryServiceClient;

    @BeforeEach
    void setUp() {
        when(orderService.updatePaymentStatus(anyLong(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(orderService.syncDeliveryStatus(anyLong(), any())).thenReturn(Mono.empty());
        when(orderService.getOrderById(100L)).thenReturn(Mono.just(OrderResponseDto.builder()
                .orderId(100L).userId(7L).status(OrderStatus.CREATED).totalAmount(new BigDecimal("302.50"))
                .items(List.of(OrderItemDto.builder().productId(1L).vendorId(50L).quantity(1).build()))
                .build()));
        when(orderService.getOrdersByUser(anyLong())).thenReturn(Flux.empty());
        when(deliveryServiceClient.isAssignedToCaller(anyLong(), any())).thenReturn(Mono.just(false));
    }

    private static String jwt(long userId, String role) {
        return Jwts.builder().setSubject("9999999999").claim("userId", userId).claim("role", role)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private WebTestClient.ResponseSpec putPaymentStatus(java.util.function.Consumer<org.springframework.http.HttpHeaders> headers) {
        return client.put().uri(BASE + "/100/payment-status").headers(headers)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"PAID\"}").exchange();
    }

    // --- P0-1: payment-status / delivery-status are internal-only ----------------------------

    @Test
    void paymentStatusWithoutCredentialsIsRejected() {
        putPaymentStatus(h -> {}).expectStatus().isUnauthorized();
        verify(orderService, never()).updatePaymentStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void paymentStatusWithWrongInternalTokenIsRejected() {
        putPaymentStatus(h -> h.set(InternalAuth.HEADER, "wrong-token-wrong-token-wrong-token-xx"))
                .expectStatus().isUnauthorized();
        verify(orderService, never()).updatePaymentStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void paymentStatusWithACustomerJwtIsForbidden() {
        putPaymentStatus(h -> h.setBearerAuth(jwt(7L, "CUSTOMER"))).expectStatus().isForbidden();
        verify(orderService, never()).updatePaymentStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void paymentStatusWithAnAdminJwtIsForbiddenToo() {
        putPaymentStatus(h -> h.setBearerAuth(jwt(1L, "ADMIN"))).expectStatus().isForbidden();
    }

    @Test
    void paymentStatusWithInternalTokenIsAccepted() {
        putPaymentStatus(h -> h.set(InternalAuth.HEADER, INTERNAL_TOKEN)).expectStatus().isOk();
        verify(orderService).updatePaymentStatus(eq(100L), eq(OrderStatus.PAID), any(), any(), any());
    }

    @Test
    void deliveryStatusWithoutInternalTokenIsRejected() {
        client.put().uri(BASE + "/100/delivery-status").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"deliveryStatus\":\"DELIVERED\"}")
                .exchange().expectStatus().isForbidden();
        verify(orderService, never()).syncDeliveryStatus(anyLong(), any());
    }

    // --- create / read require authentication + ownership ---------------------------------

    @Test
    void createOrderWithoutJwtIsRejected() {
        client.post().uri(BASE + "/create").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void getOrderWithoutJwtIsRejected() {
        client.get().uri(BASE + "/100").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void ownerCanReadOrder() {
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.orderId").isEqualTo(100);
    }

    @Test
    void otherCustomerCannotReadOrder() {
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(8L, "CUSTOMER")))
                .exchange().expectBody().jsonPath("$.data").doesNotExist()
                .jsonPath("$.status").isEqualTo("error");
    }

    @Test
    void vendorWithAnItemCanReadOrderButOtherVendorCannot() {
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(50L, "VENDOR")))
                .exchange().expectStatus().isOk();
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(51L, "VENDOR")))
                .exchange().expectBody().jsonPath("$.status").isEqualTo("error");
    }

    @Test
    void assignedDeliveryBoyCanReadOrder() {
        when(deliveryServiceClient.isAssignedToCaller(eq(100L), any())).thenReturn(Mono.just(true));
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(60L, "DELIVERY_BOY")))
                .exchange().expectStatus().isOk();
    }

    @Test
    void unassignedDeliveryBoyCannotReadOrder() {
        client.get().uri(BASE + "/100").headers(h -> h.setBearerAuth(jwt(61L, "DELIVERY_BOY")))
                .exchange().expectBody().jsonPath("$.status").isEqualTo("error");
    }

    @Test
    void internalServiceCanReadOrder() {
        client.get().uri(BASE + "/100").header(InternalAuth.HEADER, INTERNAL_TOKEN)
                .exchange().expectStatus().isOk();
    }

    @Test
    void customerCanListOnlyOwnOrders() {
        client.get().uri(BASE + "/user/7").headers(h -> h.setBearerAuth(jwt(7L, "CUSTOMER")))
                .exchange().expectStatus().isOk();
        client.get().uri(BASE + "/user/7").headers(h -> h.setBearerAuth(jwt(8L, "CUSTOMER")))
                .exchange().expectBody().jsonPath("$.status").isEqualTo("error");
        verify(orderService, times(1)).getOrdersByUser(7L);
    }
}
