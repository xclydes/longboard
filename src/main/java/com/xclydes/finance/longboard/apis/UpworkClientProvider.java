package com.xclydes.finance.longboard.apis;

import com.Upwork.api.Config;
import com.Upwork.api.OAuthClient;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.xclydes.finance.longboard.config.CacheConfig;
import com.xclydes.finance.longboard.models.Token;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

@Component
@Qualifier("upwork")
public class UpworkClientProvider implements IClientProvider<OAuthClient> {

    private final Token clientToken;
    private final String callbackUrl;

    public UpworkClientProvider(@Value("${longboard.upwork.client.key}") final String clientKey,
                                @Value("${longboard.upwork.client.secret}") final String clientSecret,
                                @Value("${longboard.upwork.client.callback}") final String callbackUrl) {
        this.callbackUrl = callbackUrl;
        this.clientToken = Token.of(clientKey, clientSecret);
    }

    public Token getClientToken() {
        return clientToken;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    private Optional<TokenResponse> toTokenResponse(final Token token) {
        Optional<TokenResponse> converted = Optional.empty();
        // If the internal token is valid
        if( token != null && token.isComplete()) {
            final TokenResponse existingToken = new TokenResponse();
            existingToken.setTokenType("Bearer");
            existingToken.setAccessToken(token.getKey());
            existingToken.setRefreshToken(token.getSecret());
            // TODO What should this actually be?
            existingToken.setExpiresInSeconds((long) 86399);
            converted = Optional.of(existingToken);
        }
        return converted;
    }

    @Override
    @Cacheable(CacheConfig.UPWORK_APICLIENT)
    public OAuthClient getClient(final Token token) {
        // Configure the client
        final Properties props = new Properties();
        props.setProperty("clientId", this.getClientToken().getKey());
        props.setProperty("clientSecret", this.getClientToken().getSecret());
        props.setProperty("redirectUri", this.getCallbackUrl());
        final Config config = new Config(props);
        // Initialize the client
        final OAuthClient oAuthClient = new OAuthClient(config);
        // process the token supplied
        this.toTokenResponse(token)
            // If the token is valid
            .ifPresent(existingToken -> {
                try {
                    // Configure the client
                    oAuthClient.setTokenResponse(existingToken, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        // Pass on the client
        return oAuthClient;
    };
}
