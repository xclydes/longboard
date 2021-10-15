package com.xclydes.finance.longboard.config;

import com.github.mkopylec.charon.configuration.CharonConfigurer;
import com.github.mkopylec.charon.forwarding.interceptors.*;
import com.xclydes.finance.longboard.component.CorsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.mkopylec.charon.configuration.CharonConfigurer.charonConfiguration;
import static com.github.mkopylec.charon.configuration.RequestMappingConfigurer.requestMapping;
import static com.github.mkopylec.charon.forwarding.Utils.copyHeaders;
import static com.github.mkopylec.charon.forwarding.interceptors.RequestForwardingInterceptorType.REQUEST_PROXY_HEADERS_REWRITER;
import static com.github.mkopylec.charon.forwarding.interceptors.rewrite.RegexRequestPathRewriterConfigurer.regexRequestPathRewriter;
import static com.github.mkopylec.charon.forwarding.interceptors.rewrite.RequestServerNameRewriterConfigurer.requestServerNameRewriter;

@Configuration
public class UpworkConfig {

    @Bean
    @Qualifier("upwork")
    RequestForwardingInterceptor longBoardInterceptor() {
        return new RequestForwardingInterceptor() {

            private Logger log = LoggerFactory.getLogger("com.xclydes.finance.longboard.config.UpworkConfig$HeaderStripper");

            @Override
            public Mono<HttpResponse> forward(HttpRequest request, HttpRequestExecution execution) {
                logStart(execution.getMappingName());
                rewriteHeaders(request.headers(), request::setHeaders);
                return execution.execute(request)
                        .doOnSuccess(response -> logEnd(execution.getMappingName()));
            }

            void rewriteHeaders(final HttpHeaders headers, final Consumer<HttpHeaders> headersSetter) {
                final HttpHeaders rewrittenHeaders = copyHeaders(headers);
                // Remove all key starting with X-forwarded
                Stream.of(rewrittenHeaders.keySet().toArray())
                    .map(s -> String.valueOf(s).toLowerCase())
                    .filter(this::shouldRemove)
                    .forEach(rewrittenHeaders::remove);
                // Rewrite the Authorization header
                if(rewrittenHeaders.containsKey("Authorization")) {
                    final String decodedAuth = URLDecoder.decode(Objects.requireNonNull(rewrittenHeaders.getFirst("Authorization")), Charset.defaultCharset());
                    rewrittenHeaders.set("Authorization", decodedAuth);
                }
                headersSetter.accept(rewrittenHeaders);
                log.debug("Request headers rewritten from {} to {}", headers, rewrittenHeaders);
            }

            boolean shouldRemove(final String headerName) {
                return !headerName.equalsIgnoreCase("authorization")
//                return headerName.startsWith("x-forwarded")
//                    || headerName.equalsIgnoreCase("x-real-ip")
//                    || headerName.equalsIgnoreCase("origin")
//                    || headerName.equalsIgnoreCase("referer")
                ;
            }

            void logStart(String mappingName) {
                log.trace("[Start] Stripping headers for '{}' request mapping", mappingName);
            }

            void logEnd(String mappingName) {
                log.trace("[End] Stripping headers for '{}' request mapping", mappingName);
            }

            @Override
            public RequestForwardingInterceptorType getType() {
                return new RequestForwardingInterceptorType(REQUEST_PROXY_HEADERS_REWRITER.getOrder() + 1);
            }
        };
    }

    @Bean
    @Qualifier("upwork")
    RequestForwardingInterceptorConfigurer<RequestForwardingInterceptor> longBoardConfigurer(final @Qualifier("upwork") RequestForwardingInterceptor longBoardInterceptor) {
        return new RequestForwardingInterceptorConfigurer<>(longBoardInterceptor) { };
    }

    @Bean
    @Qualifier("upwork")
    CharonConfigurer charonConfigurer(final CorsFilter corsFilter, final @Qualifier("upwork") RequestForwardingInterceptorConfigurer<?> longBoardConfigurer) {
        final String proxyInBase = "/proxy/upwork/";
        // Cr
        return charonConfiguration()
            .set(requestServerNameRewriter().outgoingServers("https://www.upwork.com"))
            .set(regexRequestPathRewriter()
                    .paths(proxyInBase + "(?<path>.*)", "/api/<path>"))
            .set(longBoardConfigurer)
            .add(requestMapping(proxyInBase + ".*").pathRegex(proxyInBase + ".*"))
            .unset(REQUEST_PROXY_HEADERS_REWRITER)
            .filterOrder(corsFilter.getOrder() + 1);
    }
}
