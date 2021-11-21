package com.xclydes.finance.longboard.upwork;

import com.xclydes.finance.longboard.graphql.LongboardExceptionAdapter;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Qualifier("upwork")
public class TransientExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(@NotNull final Throwable ex, @NotNull DataFetchingEnvironment env) {
        // Assume this isnt for us
        Optional<APIException> upworkException = Optional.empty();
        Throwable currentException = ex;
        while(currentException != null) {
            if( ex instanceof APIException) {
                upworkException = Optional.of((APIException) ex);
                // Stop looking
                break;
            } else {
                currentException = ex.getCause();
            }
        }
        // if there is an upwork exception
        return upworkException
            .map(this::remapError)
            .orElse(null);
    }

    private GraphQLError remapError(final APIException upworkException) {
        GraphQLError remapped;
        // Create a new type based on the code
        final Integer srcCode = upworkException.getCode();
        final String message = upworkException.getMessage();
        final Map<String, Object> extensions = Map.of(
            "code", upworkException.getCode(),
            "reason", upworkException.getReason()
        );
        // If we have a token issue
        if(srcCode == 401) {
            // This is an authentication issue
            remapped = new LongboardExceptionAdapter(ErrorType.FORBIDDEN, message, extensions);
        } else {
            // Its an internal server error
            remapped = new LongboardExceptionAdapter(ErrorType.INTERNAL_ERROR, message, extensions);
        }
        return remapped;
    }

}
