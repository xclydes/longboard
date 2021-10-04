package com.xclydes.finance.longboard.functions.wave;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.reactor.ReactorApollo;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.apis.Token;
import com.xclydes.finance.longboard.functions.AbsGraphQLAPIFunction;
import com.xclydes.finance.longboard.wave.BusinessListQuery;
import com.xclydes.finance.longboard.wave.BusinessListQuery.Businesses;
import com.xclydes.finance.longboard.wave.BusinessListQuery.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
@Qualifier("wave-graphql")
@Slf4j
public class BusinessList extends AbsGraphQLAPIFunction<Message<Void>, List<BusinessListQuery.Edge>> {


    public BusinessList(@Qualifier("wave-graphql") final IClientProvider<ApolloClient> clientProvider) {
        super(clientProvider);
    }

    @Override
    public Mono<List<BusinessListQuery.Edge>> apply(final Message<Void> stringMessage) {
        final MessageHeaders headers = stringMessage.getHeaders();
        // Get the token value from the header
        final Token token = requires(Optional
            .ofNullable(headers.get("x-proxy-authorization", String.class))
            .map(Token::of)
            .orElse(Token.EMPTY),
            Token::hasKey,
            "X-Proxy-Authorization header missing or invalid"
        );
        // Process starting with the authorization header
        return Mono.just(token)
            .mapNotNull(getClientProvider()::getClient)
            .map(client -> client.query(new BusinessListQuery(Input.optional(1), Input.optional(10))))
            .flatMap(ReactorApollo::from)
            .flatMap(response -> {
                if(response.hasErrors()) {
                    // Create a throwable error
                    return Mono.error(handleResponseErrors(response));
                }
                return Mono.justOrEmpty(response.getData());
            })
            .mapNotNull(Data::businesses)
            .map(Businesses::edges)
            .onErrorStop();
    }

}
