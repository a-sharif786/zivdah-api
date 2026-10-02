package com.zivdah.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

// Prod behaviour (zivdah.eureka.auth.enabled=true): the registry API and dashboard reject
// anonymous callers and accept the configured Basic credentials.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "zivdah.eureka.auth.enabled=true",
        "spring.security.user.name=eureka-test",
        "spring.security.user.password=eureka-test-password"
})
class EurekaSecurityTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void registryRejectsAnonymousCallers() {
        assertThat(rest.getForEntity("/eureka/apps", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registryRejectsWrongCredentials() {
        assertThat(rest.withBasicAuth("eureka-test", "wrong").getForEntity("/eureka/apps", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registryAcceptsConfiguredCredentials() {
        assertThat(rest.withBasicAuth("eureka-test", "eureka-test-password")
                .getForEntity("/eureka/apps", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
