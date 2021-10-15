package com.xclydes.finance.longboard.apis;

import com.xclydes.finance.longboard.config.CacheConfig;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.overrides.upwork.OAuthClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@Qualifier("upwork")
public class UpworkClientProvider implements IClientProvider<OAuthClient> {

    private final Token clientToken;

    public UpworkClientProvider(@Value("${longboard.upwork.client.key}") final String clientKey,
                                @Value("${longboard.upwork.client.secret}") final String clientSecret) {
        this.clientToken = Token.of(clientKey, clientSecret);
    }

    @Override
    @Cacheable(CacheConfig.UPWORK_APICLIENT)
    public OAuthClient getClient(final Token token)  {
        // Initialize the client
        final OAuthClient oAuthClient = new OAuthClient(this.clientToken);
        // If the token is valid
        if(token.isComplete()) {
            // Configure the client
            oAuthClient.setTokenWithSecret(token.getKey(), token.getSecret());
        }
        // Pass on the client
        return oAuthClient;
    };
}
