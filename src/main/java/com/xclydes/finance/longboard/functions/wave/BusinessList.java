package com.xclydes.finance.longboard.functions.wave;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloQueryCall;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.apis.Token;
import com.xclydes.finance.longboard.functions.AbsGraphQLAPIFunction;
import com.xclydes.finance.longboard.wave.BusinessListQuery;
import com.xclydes.finance.longboard.wave.BusinessListQuery.Businesses;
import com.xclydes.finance.longboard.wave.BusinessListQuery.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Qualifier("wave-graphql")
@Slf4j
public class BusinessList extends AbsGraphQLAPIFunction<Void, List<BusinessListQuery.Edge>> {


    public BusinessList(@Qualifier("wave-graphql") final IClientProvider<ApolloClient> clientProvider) {
        super(clientProvider);
    }

    @Override
    protected void doProcess(final FluxSink<List<BusinessListQuery.Edge>> sink, final Message<Void> input) {
        final MessageHeaders headers = input.getHeaders();
        // Get the token value from the header
        final Token token = requires(Optional
            .ofNullable(headers.get("x-proxy-authorization", String.class))
            .map(Token::of)
            .orElse(Token.EMPTY),
            Token::hasKey,
            "X-Proxy-Authorization header missing or invalid"
        );
        // Get the API client
        final ApolloClient client = getClientProvider().getClient(token);
        // Execute the query
        final ApolloQueryCall<Data> query = client.query(new BusinessListQuery(Input.optional(1), Input.optional(10)));
        // Process the response
        query.enqueue(new ApolloCall.Callback<Data>() {
            @Override
            public void onResponse(@NotNull Response<Data> response) {
                if(response.hasErrors()) {
                    // Create a throwable error
                    sink.error(handleResponseErrors(response));
                } else {
                    // Get the content
                    final List<BusinessListQuery.Edge> edges = Optional
                            .ofNullable(response.getData())
                            .map(Data::businesses)
                            .map(Businesses::edges)
                            .orElse(Collections.emptyList());
                    sink.next(edges);
                }
                sink.complete();
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                sink.error(e);
                sink.complete();
            }
        });
    }

}
