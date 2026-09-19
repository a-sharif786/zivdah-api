package com.zivdah.payment.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    // Reactor Netty's default resolver queries nameservers directly over UDP and can fail
    // intermittently against ordinary routers/resolvers even when they're otherwise healthy (seen
    // in practice: "Failed to resolve api.ecomworldpay.net" from this client, moments apart, while
    // the OS resolver answered every lookup fine). DefaultAddressResolverGroup delegates to the
    // JDK's resolver instead — the same one curl/nslookup use, with its normal caching and retries.
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);
        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
