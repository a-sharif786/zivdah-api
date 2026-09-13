package com.zivdah.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// Phase 1 placeholder: no client/ package or outbound WebClient calls yet (see
// OrderServiceClient/ProductServiceClient/etc. planned for Phase 4). This bean exists now so the
// shape matches every other service and Phase 4 only needs to add client wrapper classes on top
// of it, not touch this config.
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
