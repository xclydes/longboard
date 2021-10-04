package com.xclydes.finance.longboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class CorsConfig {

    @Bean
    public WebFilter corsFilter(
        @Value("${longboard.cors.methods:OPTIONS,HEAD,GET,POST}") final List<String> methodStrings,
        @Value("${longboard.cors.origins:*}") final String origins,
        @Value("${longboard.cors.headers:*}") final List<String> headers,
        @Value("${longboard.cors.max-age:3600}") final Long maxAge
    ) {
        // Map the methods
        final List<HttpMethod> methods = methodStrings.stream().map(HttpMethod::resolve)
                .collect(Collectors.toList());
        return (ServerWebExchange ctx, WebFilterChain chain) -> {
            final ServerHttpRequest request = ctx.getRequest();
            final ServerHttpResponse response = ctx.getResponse();

            // If cross-origin or preflight
            final boolean isPreflight = CorsUtils.isPreFlightRequest(request);
            if(isPreflight || CorsUtils.isCorsRequest(request)){
                // Add the CORS headers
                final HttpHeaders responseHeaders = response.getHeaders();
                responseHeaders.setAccessControlAllowCredentials(true);
                responseHeaders.setAccessControlAllowHeaders(headers);
                responseHeaders.setAccessControlAllowMethods(methods);
                responseHeaders.setAccessControlMaxAge(maxAge);

                // Resolve the correct header to use
                final Optional<String> originHeaderOpt = Optional.ofNullable(request.getHeaders().getOrigin())
                        .filter(StringUtils::hasText);
                final String resolvedOrigins = "*".equals(origins) || !StringUtils.hasText(origins) ?
                        originHeaderOpt.orElse("*"):
                        origins;
                // Set the origins
                responseHeaders.setAccessControlAllowOrigin(resolvedOrigins);

                // If this is a pre-flight request
                if (isPreflight) {
                    // It stops right there
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            // Continue processing
            return chain.filter(ctx);
        };
    }
}
