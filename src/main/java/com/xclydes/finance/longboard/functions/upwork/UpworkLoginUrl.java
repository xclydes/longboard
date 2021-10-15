package com.xclydes.finance.longboard.functions.upwork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.apis.Token;
import com.xclydes.finance.longboard.functions.AbsAPIFunction;
import com.xclydes.finance.longboard.overrides.upwork.OAuthClient;
import oauth.signpost.OAuthConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

@Component
@Qualifier("upwork")
public class UpworkLoginUrl extends AbsAPIFunction<OAuthClient, Void, JsonNode> {

    private final String callbackUrl;
    private final ObjectMapper objectMapper;

    public UpworkLoginUrl(@Qualifier("upwork") final IClientProvider<OAuthClient> clientProvider,
                          @Value("${longboard.upwork.client.callback}") final String callbackUrl,
                        final ObjectMapper objectMapper) {
        super(clientProvider);
        this.callbackUrl = callbackUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doProcess(final FluxSink<JsonNode> sink, final Message<Void> input) {
        // Get the url
        final OAuthClient client = getClientProvider().getClient();
        final OAuthConsumer oAuthConsumer = client.getOAuthConsumer();
        final String url = client.getAuthorizationUrl( this.callbackUrl );
        final Token requestToken = Token.of(oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret());
        final ObjectNode response = this.objectMapper
            .createObjectNode()
            .put("url", url)
            .putPOJO("token", requestToken);
        sink.next(response).complete();
    }
}
