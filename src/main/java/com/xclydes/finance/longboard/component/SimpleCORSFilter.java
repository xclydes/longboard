package com.xclydes.finance.longboard.component;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class SimpleCORSFilter implements Filter {

    private final String methods;
    private final boolean anyOrigin;
    private final String origins;
    private final String headers;
    private final String maxAge;

    public SimpleCORSFilter(
        @Value("${longboard.cors.methods:OPTIONS,GET,POST}") final String methods,
        @Value("${longboard.cors.origins:*}") final String origins,
        @Value("${longboard.cors.headers:*}") final String headers,
        @Value("${longboard.cors.max-age:3600}") final String maxAge
    ) {
        this.methods = methods;
        this.headers = headers;
        this.maxAge = maxAge;
        this.origins = origins;
        this.anyOrigin = "*".equals(origins) || !StringUtils.hasText(origins);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        // Resolve the correct header to use
        final Optional<String> originHeaderOpt = Optional.ofNullable(request.getHeader("Origin"))
                .filter(StringUtils::hasText);
        final String resolvedOrigins = this.anyOrigin ?
            originHeaderOpt.orElse("*"):
            this.origins;

        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", resolvedOrigins);
        response.setHeader("Access-Control-Max-Age", this.maxAge);
        response.setHeader("Access-Control-Allow-Methods", this.methods);
        response.setHeader("Access-Control-Allow-Headers", this.headers);

        chain.doFilter(req, res);
    }
}
