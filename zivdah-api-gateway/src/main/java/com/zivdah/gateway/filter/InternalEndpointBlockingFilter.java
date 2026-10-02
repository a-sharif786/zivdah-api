package com.zivdah.gateway.filter;

import com.zivdah.common.security.InternalAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
@Component
public class InternalEndpointBlockingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalEndpointBlockingFilter.class);

    private static final List<PathPattern> INTERNAL_ONLY = compile(
            "/restful/v1/api/auth/internal/**",
            "/restful/v1/api/orders/*/payment-status",
            "/restful/v1/api/orders/*/delivery-status",
            "/restful/v1/api/payments/order/*/refund",
            "/restful/v1/api/products/*/sync-stock",
            "/restful/v1/api/inventory/*/sync-quantity"
    );

    private static List<PathPattern> compile(String... patterns) {
        PathPatternParser parser = new PathPatternParser();
        return java.util.Arrays.stream(patterns).map(parser::parse).toList();
    }

    static boolean isInternalOnly(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return INTERNAL_ONLY.stream().anyMatch(p -> p.matches(container));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isInternalOnly(path)) {
            log.warn("Blocked public request to internal endpoint {} {}", exchange.getRequest().getMethod(), path);
            // 404, not 403: don't advertise that the endpoint exists.
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        if (!exchange.getRequest().getHeaders().containsKey(InternalAuth.HEADER)) {
            return chain.filter(exchange);
        }
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> h.remove(InternalAuth.HEADER)))
                .build();
        return chain.filter(stripped);
    }

    // Before routing/any other filter.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
