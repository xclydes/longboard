package com.xclydes.finance.longboard.apis;

import com.xclydes.finance.longboard.config.CacheConfig;
import com.xclydes.finance.longboard.models.Token;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static com.xclydes.finance.longboard.config.CacheConfig.CacheKeys.WAVE_APICLIENT_REST;

@Component
@Qualifier("wave-rest")
public class WaveRESTClientProvider implements IClientProvider<WebClient> {

    private final String endpointUrl;
    private final Map<String, String> headers;

    public WaveRESTClientProvider(@Value("${longboard.wave.endpoint.rest}") final String endpointUrl,
                                  @Value("#{${longboard.wave.client.headers}}") final Map<String, String> headers) {
        this.endpointUrl = endpointUrl;
        this.headers = headers;
    }

    @Override
    @Cacheable(WAVE_APICLIENT_REST)
    public WebClient getClient(final Token token) {
        final WebClient.Builder builder = WebClient
            .builder()
            .defaultHeaders(httpHeaders -> {
                // if the headers are set
                if(this.headers != null && !this.headers.isEmpty()) {
                    // Add the values specified
                    this.headers.forEach(httpHeaders::add);
                }
            })
            .baseUrl(endpointUrl);
        // if the token key is set
        if(token.hasKey()) {
            // Add the authorization header
            builder.defaultHeader("Authorization", "Bearer " + token.getKey());
        }
        // Add the base url/endpoint
        return builder.build();
    }
}
