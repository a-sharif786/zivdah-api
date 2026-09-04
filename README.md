new content




Add a Zivdah Log Server + centralized log shipping across zivdah-api
Context
zivdah-api (D:\zivdah_project\zivdah-api) is a 12-module Spring Boot/WebFlux monorepo (eureka-server, api-gateway, auth/product/cart/order/payment/notification/inventory/user/review/coupon services). Today, logging is scattered @Slf4j console output with no shared config: no request correlation, no centralized capture, and every service's GlobalExceptionHandler silently swallows exceptions without logging them. There is no existing "Zivdah Log Server" anywhere in this repo or on disk (confirmed by search).

The user runs zivdah-api as their backend server ("Zivdah Backend Server") and wants a second, new server — the Zivdah Log Server — that all of zivdah-api's services ship their logs to, so logs from the whole backend can be fetched/queried in one place.

This plan adds zivdah-log-server as a new 13th module in the existing monorepo (same Maven parent, same Eureka/Gateway/Postgres/Kafka infra it already runs), and wires every existing service to ship its logs there over Kafka — without touching existing business logic or call sites (log.info/warn/error calls keep working exactly as they are; they're additionally captured and shipped).

Architecture
[auth/product/cart/... services]  --(Logback root logger)-->  KafkaLogAppender (async)
        │                                                            │
        │ (existing Kafka broker, already in docker-compose)         ▼
        └───────────────────────────────────────────────►  topic "app-logs"
                                                                      │
                                                                      ▼
                                                        zivdah-log-server (new, port 8012)
                                                        Kafka consumer → Postgres "logs" table
                                                        REST API: GET /restful/v1/api/logs (query/filter)
                                                                      │
                                                                      ▼
                                                        zivdah-api-gateway routes /restful/v1/api/logs/**
Every service keeps writing logs exactly as today (@Slf4j, log.info(...), etc.). A new Logback appender attached to the root logger of each service asynchronously publishes each log event as JSON onto a Kafka topic; nothing about existing call sites changes. zivdah-log-server consumes that topic, persists rows into Postgres, and exposes a query API.

1. New module: zivdah-log-server
New directory D:\zivdah_project\zivdah-api\zivdah-log-server\, following the exact pattern of zivdah-order-service (reactive WebFlux + R2DBC + Flyway + Eureka + Kafka + zivdah-common), port 8012 (next free port after coupon-service 8011).

pom.xml — parent com.zivdah:zivdah-api, deps: spring-boot-starter-webflux, spring-boot-starter-data-r2dbc, r2dbc-postgresql, flyway-core + flyway-database-postgresql + postgresql, spring-cloud-starter-netflix-eureka-client, spring-kafka, zivdah-common, springdoc-openapi-starter-webflux-api, Lombok, validation, test starters — same dependency list as zivdah-order-service\pom.xml.
src/main/resources/application-dev.yaml:
server.port: 8012, spring.application.name: zivdah-log-server
spring.r2dbc.url: r2dbc:postgresql://localhost:5432/zivdahDB (shared DB, same as every other service)
spring.flyway.table: flyway_schema_history_logserver, baseline-on-migrate: true (matches the per-service dedicated Flyway history table convention already used everywhere else, e.g. zivdah-order-service\src\main\resources\application-dev.yaml:22)
spring.kafka.consumer.group-id: log-server-group, bootstrap-servers: localhost:9092, same JSON deserializer/trusted-packages config as zivdah-order-service's consumer block
eureka.client.service-url.defaultZone: http://localhost:8761/eureka
src/main/resources/db/migration/V1__create_logs_table.sql — logs table: id BIGSERIAL PK, service_name, log_level, logger_name, message TEXT, exception TEXT (nullable), correlation_id (nullable), thread_name, logged_at TIMESTAMPTZ, received_at TIMESTAMPTZ DEFAULT now(); indexes on service_name, log_level, logged_at DESC.
src/main/java/com/zivdah/logserver/:
ZivdahLogServerApplication.java — @SpringBootApplication
entity/LogEntry.java, repository/LogEntryRepository.java (ReactiveCrudRepository, plus an R2dbcEntityTemplate-based query method for dynamic filtering by service/level/date-range/text search)
kafka/LogEventConsumer.java — @KafkaListener(topics = KafkaTopics.APP_LOGS, groupId = "log-server-group"), deserializes LogEvent, maps to LogEntry, saves
controller/LogController.java — GET /restful/v1/api/logs (filters: service, level, from, to, q, page, size — paginated), GET /restful/v1/api/logs/{id}
dto/ApiResponse.java, dto/LogEntryDto.java — same wrapper pattern as every other service's dto/ApiResponse.java (e.g. zivdah-order-service\src\main\java\com\zivdah\order\dto\ApiResponse.java)
exception/GlobalExceptionHandler.java — same as other services but with logging fixed (see §4)
Dockerfile — identical pattern to zivdah-auth-service\Dockerfile (eclipse-temurin:21-jdk-alpine + copy jar)
Register the module in D:\zivdah_project\zivdah-api\pom.xml (<modules> list)
2. Shared additions in zivdah-common
D:\zivdah_project\zivdah-api\zivdah-common\src\main\java\com\zivdah\common\:

constants\KafkaTopics.java — add public static final String APP_LOGS = "app-logs";
event\LogEvent.java — new DTO shipped over Kafka: serviceName, level, loggerName, message, exception (nullable String), correlationId (nullable), threadName, loggedAt
logging\KafkaLogAppender.java — a Logback AppenderBase<ILoggingEvent> that:
lazily builds a raw KafkaProducer<String,String> (fire-and-forget send(), key = service name) from XML-configured params (bootstrapServers, topic) — no Spring bean wiring needed, so it works regardless of Spring context startup order
reads the service name from a Logback context property (context.getProperty("APP_NAME"), set per-service in each logback-spring.xml)
serializes LogEvent to JSON via a shared static Jackson ObjectMapper, pulls stack trace via ThrowableProxyUtil.asString(...) when present, pulls correlationId from the event's MDC map if present
wrapped by Logback's built-in AsyncAppender (no extra dependency) so log shipping never blocks the calling thread
new deps needed in zivdah-common\pom.xml: org.apache.kafka:kafka-clients (or rely on it transitively via spring-kafka, already provided/declared like the webflux dep is), ch.qos.logback:logback-classic (provided, already transitive via spring-boot-starter)
logging\kafka-appender-include.xml (in src/main/resources/logback/) — the actual <appender> + <asyncAppender> XML definition, referenced via Logback's <include resource="logback/kafka-appender-include.xml"/> from each service's own thin logback-spring.xml (Logback's <include> resolves through the classloader, so this works across the jar boundary — same "define once in common, thin per-service wiring" pattern already used for CloudinaryUploadService, see zivdah-product-service\src\main\java\com\zivdah\product\config\CloudinaryConfig.java)
logging\CorrelationIdWebFilter.java — reactive WebFilter (highest precedence): reads incoming X-Correlation-Id header or generates a UUID, echoes it back on the response, and sets it in MDC for the synchronous portion of request handling. Noted as best-effort: full MDC propagation across every async hop in a WebFlux reactive chain is not guaranteed without additional Reactor context-propagation wiring; this filter gives correlation IDs on the request/response headers reliably, and in-log correlation IDs where the logging call happens to be on the request thread. Treat deeper reactive MDC propagation as a follow-up, not a blocking requirement for this change.
3. Wiring every existing service (representative: zivdah-order-service; same change repeats for all 12 services + gateway + eureka-server)
For each of zivdah-auth-service, zivdah-product-service, zivdah-cart-service, zivdah-order-service, zivdah-payment-service, zivdah-notification-service, zivdah-inventory-service, zivdah-user-service, zivdah-review-service, zivdah-coupon-service, zivdah-api-gateway, zivdah-eureka-server:

Add src/main/resources/logback-spring.xml:
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <property name="APP_NAME" value="${spring.application.name:-unknown-service}"/>
    <include resource="logback/kafka-appender-include.xml"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>${CONSOLE_LOG_PATTERN}</pattern></encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_KAFKA"/>
    </root>
</configuration>
Ensure zivdah-common is a declared dependency (already true for all 10 business services, e.g. zivdah-order-service\pom.xml:64-68; needs adding to zivdah-api-gateway\pom.xml and zivdah-eureka-server\pom.xml, which currently don't depend on it)
Ensure spring-kafka (or kafka-clients) is present (already true for the 10 business services; needs adding to zivdah-api-gateway\pom.xml and zivdah-eureka-server\pom.xml)
Ensure spring.kafka.producer.bootstrap-servers: localhost:9092 (or the shared spring.kafka.bootstrap-servers) exists in each service's application-dev.yaml — already present in the business services (e.g. zivdah-order-service\src\main\resources\application-dev.yaml:27-34); needs adding to the gateway and eureka-server configs
Register CorrelationIdWebFilter as a @Bean in each service's existing config\SecurityConfig.java (or a new small WebFilterConfig.java where no such config class exists yet, e.g. gateway/eureka), same explicit-wiring pattern already used for CloudinaryUploadService (CloudinaryConfig.java)
4. Fix the logging gaps the exploration surfaced (now that logs are actually centralized, these matter)
GlobalExceptionHandler.java never logs (confirmed empty of any log.* call in every service, e.g. zivdah-order-service\src\main\java\com\zivdah\order\exception\GlobalExceptionHandler.java) — add @Slf4j + log.warn(...) on the validation/runtime handlers and log.error(...) with the full exception on the generic Exception handler, across all 10 services that have this class.
FirebaseConfig.java:31 (zivdah-notification-service) uses raw System.out.println(...) which bypasses SLF4J entirely and would never reach the log server — change to log.info(...).
JwtAuthenticationFilter.java copies are inconsistent: auth-service, user-service, product-service log full JWT claims (Mobile, Role, UserId, raw Authorization header) at INFO level — once these ship to a centralized, more widely-queryable log store, that's a PII/security exposure at the default log level. Downgrade the claim-dump lines to DEBUG and keep only a safe one-line summary (e.g. "JWT validated for user {id}") at INFO, applied consistently across all copies of this filter (including the ones with no logging at all today, e.g. zivdah-coupon-service, which get a minimal INFO/DEBUG pair added for parity).
5. Gateway route
Add to zivdah-api-gateway\src\main\resources\application-dev.yaml routes list (same pattern as the existing 10 entries, e.g. lines 43-46 for orders):

- id: zivdah-log-server
  uri: lb://zivdah-log-server
  predicates:
    - Path=/restful/v1/api/logs/**
(plus the matching OpenAPI aggregation route + swagger-ui entry, same as the other services' -openapi route pairs.)

Out of scope (flagged, not building now)
No changes to zivdah-admin/zivdah-web/zivdah-flutter — a log-viewer UI isn't part of this request; can be a follow-up against the new GET /restful/v1/api/logs endpoint.
No docker-compose.yml changes needed — Postgres/Kafka/Zookeeper already run there; zivdah-log-server reuses them like every other module.
Full reactive MDC/correlation-ID propagation across async boundaries is noted as best-effort/follow-up (see §2), not a hard requirement of this change.
Verification
docker compose up -d (redis, postgres-db, zookeeper, kafka) from D:\zivdah_project\zivdah-api
mvn clean install at the repo root — confirms zivdah-log-server compiles/packages alongside all 12 existing modules
Start zivdah-eureka-server, then zivdah-log-server, then zivdah-api-gateway + zivdah-order-service (good candidate — Kafka already fully wired there)
Hit an order-service endpoint (via the gateway) that triggers both normal log.info calls and a validation error (to exercise the now-logging GlobalExceptionHandler)
curl http://localhost:8012/restful/v1/api/logs?service=zivdah-order-service (or via gateway http://localhost:8001/restful/v1/api/logs/...) and confirm the triggered log lines appear with correct serviceName, level, message, timestamps
Optionally, run a Kafka console consumer on the app-logs topic to confirm messages are flowing before they even reach the log server, to isolate any Postgres/consumer-side issue from the shipping side
Confirm existing business responses (order creation etc.) are unaffected — the appender is async and additive, not in the request path