package com.xclydes.finance.longboard.config;

import com.Upwork.api.OAuthClient;
import com.google.api.client.http.HttpRequestInitializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UpworkConfig {

    @Bean
    @Qualifier("upwork")
    public HttpRequestInitializer customIntializer(
            @Value("${longboard.upwork.client.timeout.connect:30000}") final int clientConnectTimeout,
            @Value("${longboard.upwork.client.timeout.read:60000}") final int clientReadTimeout
    ) {
        final HttpRequestInitializer initializer = request -> request.setReadTimeout(clientReadTimeout)
                .setConnectTimeout(clientConnectTimeout)
                .setThrowExceptionOnExecuteError(false);
        OAuthClient.setRequestInitializer(initializer);
        return initializer;
    }

    @Bean("longboardUpworkCacheKeyGen")
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            final Object[] compoundArgs = new Object[params.length + 3];
            compoundArgs[0] = "upwor";
            compoundArgs[1] = method.getDeclaringClass().getName();
            compoundArgs[2] = method.getName();
            System.arraycopy(params, 0, compoundArgs, 3, params.length);
            return new SimpleKey(compoundArgs);
        };
    }

}
