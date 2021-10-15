package com.xclydes.finance.longboard.util;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloQueryCall;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.xclydes.finance.longboard.models.Token;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GraphQLUtil {

    public static ResponseStatusException translateResponseErrors(final Response<?> response){
        // Create a throwable error
        return Optional.ofNullable(response.getErrors())
                .orElse(Collections.emptyList())
                .stream()
                .findFirst()
                .map(error -> new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, error.getMessage()))
                .orElse(new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "API responded with an error"));
    }

    public static void throwErrors(final Response<?> response) {
        if (response.hasErrors()) {
            // Create a throwable error
            throw GraphQLUtil.translateResponseErrors(response);
        }
    }


    public static <E, N> List<N> unwrapNestedElement(Collection<E> edges, final Function<E, N> extractor) {
        List<N> nodes = Collections.emptyList();
        // If there are enough elements
        if (!edges.isEmpty()) {
            nodes = edges.stream().map(extractor)
                    .collect(Collectors.toList());
        }
        return nodes;
    }

    public static <O, R> Mono<O> processQuery(final ApolloClient client,
                                        final Query<?, R, ?> query,
                                        final Function<Response<R>, O> onResponse) {
        return processQuery(client, query, onResponse, null);
    }

    public static <O, R> Mono<O> processQuery(final ApolloClient client,
                                        final Query<?, R, ?> query,
                                        final Function<Response<R>, O> onResponse,
                                        final Consumer<ApolloException> onFailure) {
        return Mono.<O>create(sink -> {
            // Execute the query
            final ApolloQueryCall<R> queryCall = client.query(query);
            // Process the response
            queryCall.enqueue(new ApolloCall.Callback<>() {
                @Override
                public void onResponse(@NotNull Response<R> response) {
                    try {
                        // Pass it to the handler
                        final O result = onResponse.apply(response);
                        // Have the sink complete it
                        sink.success(result);
                    } catch (Throwable t) {
                        // Pass it as an error to the sink
                        sink.error(t);
                    }
                }

                @Override
                public void onFailure(@NotNull ApolloException e) {
                    // if there is a failure callback
                    if (onFailure != null) {
                        try {
                            // Let the callback handle it
                            onFailure.accept(e);
                        } catch (Throwable t) {
                            // Pass it as an error to the sink
                            sink.error(t);
                        }
                    } else {
                        // Let the sink handle it directly
                        sink.error(e);
                    }
                }
            });
        }).onErrorStop();
    }
}
