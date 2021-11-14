package com.xclydes.finance.longboard.config;

import com.Upwork.api.UpworkRestClient;
import com.google.api.client.http.HttpRequestInitializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
        UpworkRestClient.setCustomInitializer(initializer);
        return initializer;
    }
}
