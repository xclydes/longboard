package com.xclydes.finance.longboard.apis;

import com.apollographql.apollo.ApolloClient;
import com.xclydes.finance.longboard.component.HttpLoggingInterceptor;
import com.xclydes.finance.longboard.config.CacheConfig;
import com.xclydes.finance.longboard.models.Token;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.xclydes.finance.longboard.config.CacheConfig.CacheKeys.WAVE_APICLIENT_GRAPHQL;

@Component
@Qualifier("wave-graphql")
public class WaveGraphQLClientProvider implements IClientProvider<ApolloClient> {

    private final String endpointUrl;
    private final Optional<Interceptor> logInterceptorOpt;

    public WaveGraphQLClientProvider(@Value("${longboard.wave.endpoint.graghql}") final String endpointUrl,
                                     @Value("${longboard.wave.client.debug}") final Boolean debug) {
        this.endpointUrl = endpointUrl;
        // If debugging is to be enabled
        this.logInterceptorOpt = debug ?
                Optional.of(new HttpLoggingInterceptor()) :
                Optional.empty();
    }

    @Override
    @Cacheable(WAVE_APICLIENT_GRAPHQL)
    public ApolloClient getClient(final Token token) {
        // Build the apollo client
        final ApolloClient.Builder apolloBldr = ApolloClient.builder().serverUrl(this.endpointUrl);
        // Configure the HTTP client
        final OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder();
        // If the token has a non-empty key
        if(token.hasKey()) {
            // Add the Authorization header interceptor
            okHttpBuilder.addInterceptor(chain -> {
                final Request modifiedRequest = chain.request()
                        .newBuilder()
                        .addHeader("Authorization", "Bearer " + token.getKey())
                        .build();
                return chain.proceed(modifiedRequest);
            });
        }
        // if the HTTP interceptor is present
        logInterceptorOpt.ifPresent(okHttpBuilder::addInterceptor);
        // Set the HTTP client on Apollo
        apolloBldr.okHttpClient(okHttpBuilder.build());
        // Finish building Apollo
        return apolloBldr.build();
    }
}
