package com.zivdah.common.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Shared Actuator defaults for every service, so the 16 application yamls don't each repeat them.
 * Added as the LOWEST-precedence property source: any service yaml or env var still overrides.
 * Registered in META-INF/spring.factories.
 */
public class MonitoringEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Map<String, Object> DEFAULTS = Map.of(
            "management.endpoints.web.exposure.include", "health,info,metrics",
            // /actuator/health is unauthenticated (see MonitoringBasicAuthWebFilter), so it only
            // ever says UP/DOWN — no DB/Redis/Kafka hostnames or versions.
            "management.endpoint.health.show-details", "never",
            "management.info.java.enabled", "true",
            "management.info.os.enabled", "true");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("zivdahMonitoringDefaults", DEFAULTS));
    }
}
