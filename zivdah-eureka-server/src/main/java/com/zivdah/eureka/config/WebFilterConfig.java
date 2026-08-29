package com.zivdah.eureka.config;

import com.zivdah.common.logging.CorrelationIdWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebFilterConfig {

    @Bean
    public CorrelationIdWebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }
}
