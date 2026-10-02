package com.zivdah.common.security;

/**
 * Shared constants for service-to-service ("internal") calls — the endpoints that exist only for
 * another Zivdah service to call (order-service's /payment-status, payment-service's
 * /order/{id}/refund, auth-service's /internal/**, ...).
 *
 * <p>Deliberately free of any Spring Security import: zivdah-api-gateway (which has no Spring
 * Security on its classpath) reads {@link #HEADER} too, to strip it from every inbound public
 * request before routing — see the gateway's InternalEndpointBlockingFilter.
 */
public final class InternalAuth {

    /** Carries the shared internal API token on a service-to-service request. */
    public static final String HEADER = "X-Internal-Token";

    /** Authority granted to a request that presented a valid {@link #HEADER}. */
    public static final String ROLE = "SERVICE";

    /** Principal name of that authentication — never a numeric user id, so it can't be mistaken for one. */
    public static final String PRINCIPAL = "internal-service";

    private InternalAuth() {
    }
}
