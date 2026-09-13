package com.zivdah.chat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First rate-limiting precedent in the platform (see the plan's security section) — deliberately
 * scoped only to {@code /restful/v1/api/chat/**} via this filter's own path check, not global.
 * In-memory {@link ConcurrentHashMap} of buckets (no Redis dependency, matching the in-memory
 * {@code SessionRegistry} choice elsewhere in this service) — a known single-instance limitation,
 * fine for now, same as that registry's own note about horizontal scaling later.
 *
 * <p>Keyed by remote IP rather than the authenticated JWT userId: a plain {@link WebFilter} bean
 * is not guaranteed to run after {@code JwtAuthenticationFilter} in the WebFlux security filter
 * chain (that filter is wired in via {@code addFilterAt(..., AUTHENTICATION)} inside
 * {@code SecurityConfig}, a different registration mechanism than a component-scanned
 * {@code WebFilter}), so the authenticated principal isn't reliably available at this point.
 * Remote IP is simpler and still a legitimate first-pass abuse guard; keying by userId would be a
 * reasonable follow-up once this filter is wired to run strictly after authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RateLimitWebFilter implements WebFilter {

    private static final String CHAT_PATH_PREFIX = "/restful/v1/api/chat/";
    private static final String WS_PATH_PREFIX = CHAT_PATH_PREFIX + "ws/chat/";

    // Message/action-shaped endpoints get the tighter budget; everything else under the chat
    // prefix (reads, roster, analytics) gets a looser one.
    private static final int MESSAGE_LIMIT = 20;
    private static final Duration MESSAGE_WINDOW = Duration.ofSeconds(10);
    private static final int DEFAULT_LIMIT = 60;
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(10);

    private static final byte[] TOO_MANY_REQUESTS_BODY = ("{\"status\":\"error\",\"statusCode\":429,"
            + "\"message\":\"Too many requests — please slow down.\",\"data\":null}")
            .getBytes(StandardCharsets.UTF_8);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // WebSocket handshakes aren't a repeatable-request pattern the same way REST calls are —
        // rate-limiting the initial upgrade isn't useful the same way; the handler's own
        // message-size cap and per-connection nature bound abuse there instead.
        if (!path.startsWith(CHAT_PATH_PREFIX) || path.startsWith(WS_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String clientKey = clientKey(exchange);
        // GET .../messages is a read/poll (initial load, WS-reconnect catch-up, React Query
        // refetch) and shares its path suffix with POST .../messages (an actual send) — only the
        // write counts as message-shaped traffic, or a normal read burst trips the send budget.
        boolean tight = isTightlyLimitedPath(path) && exchange.getRequest().getMethod() != HttpMethod.GET;
        String bucketKey = clientKey + (tight ? ":tight" : ":default");
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> newBucket(tight));

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit exceeded for {} on {}", clientKey, path);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(TOO_MANY_REQUESTS_BODY)));
    }

    private boolean isTightlyLimitedPath(String path) {
        return path.endsWith("/bot/message") || path.endsWith("/bot/confirm")
                || path.endsWith("/messages") || path.endsWith("/rating")
                || path.endsWith("/request-human") || path.endsWith("/attachments");
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    private Bucket newBucket(boolean tight) {
        Bandwidth limit = tight
                ? Bandwidth.classic(MESSAGE_LIMIT, Refill.greedy(MESSAGE_LIMIT, MESSAGE_WINDOW))
                : Bandwidth.classic(DEFAULT_LIMIT, Refill.greedy(DEFAULT_LIMIT, DEFAULT_WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
