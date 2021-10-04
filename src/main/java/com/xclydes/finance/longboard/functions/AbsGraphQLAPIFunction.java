package com.xclydes.finance.longboard.functions;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.xclydes.finance.longboard.apis.IClientProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public abstract class AbsGraphQLAPIFunction<I,R> extends AbsAPIFunction<ApolloClient, I,R> {

    public AbsGraphQLAPIFunction(final IClientProvider<ApolloClient> clientProvider) {
        super(clientProvider);
    }

    public static Throwable handleResponseErrors(final Response<?> response){
        // Create a throwable error
        return response
                .getErrors()
                .stream()
                .findFirst()
                .map(error -> new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, error.getMessage()))
                .orElse(new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "API responded with an error"));
    }
}
