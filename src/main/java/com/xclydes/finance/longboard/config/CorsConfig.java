package com.xclydes.finance.longboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class CorsConfig implements WebFluxConfigurer {

    private final String mapping;
    private final String[] methods;
    private final String[] origins;
    private final String[] headers;

    public CorsConfig(
            @Value("${longboard.cors.mapping:/**}") final String mapping,
            @Value("${longboard.cors.methods:OPTIONS,HEAD,GET,POST}") final String[] methods,
            @Value("${longboard.cors.origins:*}") final String[] origins,
            @Value("${longboard.cors.headers:*}") final String[] headers
    ) {
        this.mapping = mapping;
        this.methods = methods;
        this.origins = origins;
        this.headers = headers;
    }

    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping(mapping)
            .allowedMethods(methods)
            .allowedOrigins(origins)
            .allowedHeaders(headers);
    }
}
