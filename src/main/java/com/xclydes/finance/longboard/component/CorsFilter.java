package com.xclydes.finance.longboard.component;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Getter
public class CorsFilter implements OrderedWebFilter {

    private final String origins;
    private final List<String> headers;
    private final Long maxAge;
    private final List<HttpMethod> methods;
    private final boolean anyOrigin;
    private final int order;

    public CorsFilter(
            @Value("${longboard.cors.methods:OPTIONS,HEAD,GET,POST}") final List<String> methodStrings,
            @Value("${longboard.cors.origins:*}") final String origins,
            @Value("${longboard.cors.headers:*}") final List<String> headers,
            @Value("${longboard.cors.max-age:3600}") final Long maxAge,
            @Value("${longboard.cors.order:}") final Integer filterOrder
    ) {
        this.methods = methodStrings.stream().map(HttpMethod::resolve)
                .collect(Collectors.toList());
        this.origins = origins;
        this.headers = headers;
        this.maxAge = maxAge;
        this.anyOrigin = "*".equals(origins) || !StringUtils.hasText(origins);
        this.order = Optional.ofNullable(filterOrder).orElse(HIGHEST_PRECEDENCE);
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange ctx, @NotNull WebFilterChain chain) {
        final ServerHttpRequest request = ctx.getRequest();
        final ServerHttpResponse response = ctx.getResponse();

        // If cross-origin or preflight
        final boolean isPreflight = CorsUtils.isPreFlightRequest(request);
        if (isPreflight || CorsUtils.isCorsRequest(request)) {
            // Add the CORS headers
            final HttpHeaders responseHeaders = response.getHeaders();
            responseHeaders.setAccessControlAllowCredentials(true);
            responseHeaders.setAccessControlAllowHeaders(getHeaders());
            responseHeaders.setAccessControlAllowMethods(getMethods());
            responseHeaders.setAccessControlMaxAge(getMaxAge());

            // Resolve the correct header to use
            final Optional<String> originHeaderOpt = Optional.ofNullable(request.getHeaders().getOrigin())
                    .filter(StringUtils::hasText);
            final String resolvedOrigins = this.isAnyOrigin() ?
                    originHeaderOpt.orElse("*") :
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
    }
}
