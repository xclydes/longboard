package com.xclydes.finance.longboard.controllers;

import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.overrides.upwork.OAuthClient;
import oauth.signpost.OAuthConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.CorePublisher;

@Controller
public class UpworkController extends AbsAPIController<OAuthClient> {

    private final String callbackUrl;


    public UpworkController(final IClientProvider<OAuthClient> clientProvider,
                          @Value("${longboard.upwork.client.callback}") final String callbackUrl) {
        super(clientProvider);
        this.callbackUrl = callbackUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    @QueryMapping
    public CorePublisher<RequestToken> upworkLogin() {
        return wrapLogic((sink) -> {
            // Get the url
            final OAuthClient client = getClientProvider().getClient();
            final OAuthConsumer oAuthConsumer = client.getOAuthConsumer();
            final String url = client.getAuthorizationUrl( this.getCallbackUrl() );
            sink.success(new RequestToken(oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret(), url));
        });
    }

    @QueryMapping
    public CorePublisher<Token> upworkAccessToken(@Argument final String verifier,
                                                @Argument final Token token) {
        return wrapLogic((sink) -> {
            // Get the url
            final OAuthClient client = getClientProvider().getClient();
            // Set the request keys on the consumer
            client.getOAuthConsumer().setTokenWithSecret(token.getKey(), token.getSecret());
            // Perform the exchange
            final Token accessToken = client.getAccessTokenSet(verifier);
            // Convert the map to a accessToken
            sink.success(accessToken);
        });
    }
}
