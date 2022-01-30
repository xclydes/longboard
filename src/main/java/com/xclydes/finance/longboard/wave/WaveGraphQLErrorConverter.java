package com.xclydes.finance.longboard.wave;

import com.xclydes.finance.longboard.graphql.LongboardExceptionAdapter;
import graphql.GraphQLError;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
@Qualifier("upwork")
public class WaveGraphQLErrorConverter implements Converter<Throwable, GraphQLError> {

    @Override
    public GraphQLError convert(@NotNull final Throwable source) {
        GraphQLError remapped = null;
        if( source instanceof APIException) {
            // Stop looking
            remapped = remapError((APIException) source);
        }
        return remapped;
    }

    private GraphQLError remapError(final APIException waveException) {
        GraphQLError remapped;
        // Create a new type based on the code
        final String srcCode = waveException.getCode();
        final String message = waveException.getMessage();
        final Map<String, Object> extensions = Map.of(
                "code", waveException.getCode(),
                "reason", waveException.getReason()
        );
        // If we have a token issue
        if(Objects.equals(srcCode, "401")) {
            // This is an authentication issue
            remapped = new LongboardExceptionAdapter(ErrorType.FORBIDDEN, message, extensions);
        } else {
            // Its an internal server error
            remapped = new LongboardExceptionAdapter(ErrorType.INTERNAL_ERROR, message, extensions);
        }
        return remapped;
    }
}
