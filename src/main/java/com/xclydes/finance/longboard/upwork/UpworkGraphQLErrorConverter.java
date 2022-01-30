package com.xclydes.finance.longboard.upwork;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.HttpResponseException;
import com.xclydes.finance.longboard.graphql.LongboardExceptionAdapter;
import graphql.GraphQLError;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Qualifier("upwork")
public class UpworkGraphQLErrorConverter implements Converter<Throwable, GraphQLError> {

    @Override
    public GraphQLError convert(@NotNull final Throwable source) {
        GraphQLError remapped = null;
        if( source instanceof APIException) {
            // Stop looking
            remapped = remapError((APIException) source);
        } else if( source instanceof TokenResponseException) {
            // Stop looking
            remapped = remapError((TokenResponseException) source);
        } else if( source instanceof HttpResponseException) {
            // Stop looking
            remapped = remapError((HttpResponseException) source);
        }
        return remapped;
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

    private GraphQLError remapError(final TokenResponseException tknResponse) {
        final TokenErrorResponse details = tknResponse.getDetails();
        final Map<String, Object> extensions = Map.of(
                "code", tknResponse.getStatusCode(),
                "reason", details.getError()
        );
        return new LongboardExceptionAdapter(
                ErrorType.FORBIDDEN,
                details.getErrorDescription(),
                extensions
        );
    }


    private GraphQLError remapError(final HttpResponseException tknResponse) {
        final Map<String, Object> extensions = Map.of(
                "code", tknResponse.getStatusCode(),
                "reason", tknResponse.getStatusMessage()
        );
        return new LongboardExceptionAdapter(
                ErrorType.FORBIDDEN,
                tknResponse.getMessage(),
                extensions
        );
    }

}
