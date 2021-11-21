package com.xclydes.finance.longboard.apis;

import com.xclydes.finance.longboard.config.CacheConfig;
import com.xclydes.finance.longboard.models.Token;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static com.xclydes.finance.longboard.config.CacheConfig.CacheKeys.WAVE_APICLIENT_REST;

@Component
@Qualifier("wave-rest")
public class WaveRESTClientProvider implements IClientProvider<WebClient> {

    private final String endpointUrl;

    public WaveRESTClientProvider(@Value("${longboard.wave.endpoint.rest}") final String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    @Override
    @Cacheable(WAVE_APICLIENT_REST)
    public WebClient getClient(final Token token) {
        final WebClient.Builder builder = WebClient
            .builder()
            .baseUrl(endpointUrl);
        // if the token key is set
        if(token.hasKey()) {
            // Add the authorization header
            builder.defaultHeader("Authorization", "Bearer" + token.getKey());
        }
        // Add the base url/endpoint
        return builder.build();
    }
}
