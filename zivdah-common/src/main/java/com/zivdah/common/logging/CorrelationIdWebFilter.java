package com.zivdah.common.logging;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Generates (or forwards) a per-request correlation id so calls for the same request can be
 * tied together across services. Always sets/echoes the {@value #HEADER_NAME} request/response
 * header. Also places the id in the Reactor {@link Context} under {@link #CONTEXT_KEY}, so any
 * handler that wants to tag its own log lines can read it explicitly, e.g.:
 * {@code Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault(CorrelationIdWebFilter.CONTEXT_KEY, "")))}.
 *
 * <p>Deliberately does <b>not</b> attempt to bridge this into SLF4J's thread-local MDC
 * automatically. WebFlux hops across threads for most async work (R2DBC, WebClient, Kafka), so
 * a naive {@code MDC.put(...)} / {@code MDC.remove(...)} wrapped around
 * {@code chain.filter(exchange)} would run and unwind before the downstream chain actually
 * executes (that call just returns a not-yet-subscribed {@code Mono}) — it would never reflect
 * on the log lines produced while handling the request. Real per-request MDC propagation across
 * reactive operators needs Reactor's context-propagation hooks (e.g. via
 * {@code micrometer-context-propagation}, not currently a dependency of this project) — treat
 * that as a follow-up, not something this filter provides today.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        return chain.filter(exchange)
                .contextWrite(Context.of(CONTEXT_KEY, correlationId));
    }
}
