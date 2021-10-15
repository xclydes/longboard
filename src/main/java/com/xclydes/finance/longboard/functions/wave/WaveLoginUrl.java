package com.xclydes.finance.longboard.functions.wave;

import com.apollographql.apollo.ApolloClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.functions.AbsAPIFunction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.FluxSink;

import java.util.List;

@Component
@Qualifier("wave-graphql")
public class WaveLoginUrl extends AbsAPIFunction<ApolloClient, JsonNode, String> {

    private final String loginUrl;
    private final String clientId;
    private final List<String> scopes;
    private final String callback;

    public WaveLoginUrl(@Qualifier("wave-graphql") final IClientProvider<ApolloClient> clientProvider,
                        @Value("${longboard.wave.oauth.login}") final String url,
                        @Value("${longboard.wave.client.key}") final String clientId,
                        @Value("${longboard.wave.oauth.scopes}") final List<String> scopes,
                        @Value("${longboard.wave.oauth.callback}") final String callback
    ) {
        super(clientProvider);
        this.loginUrl = url;
        this.clientId = clientId;
        this.scopes = scopes;
        this.callback = callback;
    }

    @Override
    protected void doProcess(final FluxSink<String> sink, final Message<JsonNode> msg) {
        // Get the message body
        final JsonNode payload = msg.getPayload();
        // Get the headers
        final MessageHeaders headers = msg.getHeaders();
        // Get the state from the request
        final String state = requires(
                payload.required("state").asText(),
            s -> StringUtils.hasText(StringUtils.trimWhitespace(s)),
            "The state path parameter is required"
        );
        // There may be a callback url
        if(payload.has("callback")) {

        }
        final UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
            .fromHttpUrl(this.loginUrl)
            .queryParam("response_type", "code")
            // Add the state attribute
            .queryParam("state", state)
            // Add the client ID
            .queryParam("client_id", this.clientId)
            // Add the scopes
            .queryParam("scope", String.join(" ", this.scopes));
        // If a callback is set
        if(StringUtils.hasText(this.callback)) {
            // Add the callback URL
            uriComponentsBuilder.queryParam("redirect_uri", this.callback);
        }
        // return the built URL
        sink.next(uriComponentsBuilder.toUriString()).complete();
    }
}
