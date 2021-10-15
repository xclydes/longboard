package com.xclydes.finance.longboard.functions.upwork;

import com.fasterxml.jackson.databind.JsonNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.apis.Token;
import com.xclydes.finance.longboard.functions.AbsAPIFunction;
import com.xclydes.finance.longboard.overrides.upwork.OAuthClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import java.util.Objects;

@Component
@Qualifier("upwork")
public class UpworkAccessToken extends AbsAPIFunction<OAuthClient, JsonNode, Token> {


    public UpworkAccessToken(@Qualifier("upwork") final IClientProvider<OAuthClient> clientProvider) {
        super(clientProvider);
    }

    @Override
    protected void doProcess(final FluxSink<Token> sink, final Message<JsonNode> input) {
        // Get the JSON body
        final JsonNode body = requires(input.getPayload(), Objects::nonNull, "A valid verifier is required");
        final String reqToken = body.required("token").asText();
        final String reqSecret = body.required("secret").asText();
        // Get the verifier
        final String verifier = body.required("verifier").asText();
        // Get the url
        final OAuthClient client = getClientProvider().getClient();
        // Set the request keys on the consumer
        client.getOAuthConsumer().setTokenWithSecret(reqToken, reqSecret);
        // Perform the exchange
        final Token token = client.getAccessTokenSet(verifier);
        // Convert the map to a token
        sink.next(token);
        sink.complete();
    }
}
