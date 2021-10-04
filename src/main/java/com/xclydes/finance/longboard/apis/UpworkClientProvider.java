package com.xclydes.finance.longboard.apis;

import com.Upwork.api.Config;
import com.Upwork.api.OAuthClient;
import com.xclydes.finance.longboard.config.CacheConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Qualifier("upwork")
public class UpworkClientProvider implements IClientProvider<OAuthClient> {

    private final String clientKey;
    private final String clientSecret;

    public UpworkClientProvider(@Value("${longboard.upwork.client.key}") final String clientKey,
                                @Value("${longboard.upwork.client.secret}") final String clientSecret) {
        this.clientKey = clientKey;
        this.clientSecret = clientSecret;
    }

    @Override
    @Cacheable(CacheConfig.UPWORK_APICLIENT)
    public OAuthClient getClient(final Token token)  {
        final Properties keys = new Properties();
        keys.setProperty("consumerKey", clientKey);
        keys.setProperty("consumerSecret", clientSecret);
        // Build the config
        final Config config = new Config(keys);
        // Initialize the client
        final OAuthClient oAuthClient = new OAuthClient(config);
        // If the token is valid
        if(token.isComplete()) {
            // Configure the client
            oAuthClient.setTokenWithSecret(token.getKey(), token.getSecret());
        }
        // Pass on the client
        return oAuthClient;
    };
}
