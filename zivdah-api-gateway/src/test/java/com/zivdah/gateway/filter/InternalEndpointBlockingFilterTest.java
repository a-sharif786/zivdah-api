package com.zivdah.gateway.filter;

import com.zivdah.common.security.InternalAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalEndpointBlockingFilterTest {

    private final InternalEndpointBlockingFilter filter = new InternalEndpointBlockingFilter();

    private static class RecordingChain implements GatewayFilterChain {
        final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            forwarded.set(exchange);
            return Mono.empty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/restful/v1/api/orders/42/payment-status",
            "/restful/v1/api/orders/42/delivery-status",
            "/restful/v1/api/payments/order/42/refund",
            "/restful/v1/api/auth/internal/users/7",
            "/restful/v1/api/auth/internal/admin-ids",
            "/restful/v1/api/auth/internal/device-tokens/deactivate",
            "/restful/v1/api/products/5/sync-stock",
            "/restful/v1/api/inventory/5/sync-quantity"
    })
    void internalEndpointsAre404FromThePublicInternet(String path) {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put(path));
        filter.filter(exchange, chain).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chain.forwarded.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/restful/v1/api/orders/42",
            "/restful/v1/api/orders/create",
            "/restful/v1/api/orders/42/status",
            "/restful/v1/api/payments/initiate",
            "/restful/v1/api/payments/11/link-order",
            "/restful/v1/api/payments/11/gateway-status",
            "/restful/v1/api/payments/callback/ecomworldpay",
            "/restful/v1/api/payments/payouts/callback",
            "/restful/v1/api/payments/order/42",
            "/restful/v1/api/auth/login",
            "/restful/v1/api/products/5"
    })
    void publicEndpointsPassThrough(String path) {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        filter.filter(exchange, chain).block();
        assertThat(chain.forwarded.get()).isNotNull();
    }

    @Test
    void inboundInternalTokenHeaderIsStripped() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/restful/v1/api/orders/42").header(InternalAuth.HEADER, "guess"));
        filter.filter(exchange, chain).block();
        assertThat(chain.forwarded.get().getRequest().getHeaders().containsKey(InternalAuth.HEADER)).isFalse();
    }
}
