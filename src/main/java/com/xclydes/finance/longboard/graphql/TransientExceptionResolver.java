package com.xclydes.finance.longboard.graphql;

import com.xclydes.finance.longboard.wave.APIException;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransientExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private final List<Converter<Throwable, GraphQLError>> converters;

    public TransientExceptionResolver(final List<Converter<Throwable, GraphQLError>> converters) {
        this.converters = converters;
    }

    @Override
    protected GraphQLError resolveToSingleError(@NotNull final Throwable ex, @NotNull DataFetchingEnvironment env) {
        // Assume this isn't for us
        Throwable currentException = ex;
        while(currentException != null) {
            // Is there a converter that can handle this exception
            for (Converter<Throwable, GraphQLError> converter: this.converters){
                final GraphQLError remapped = converter.convert(currentException);
                if( remapped != null ) {
                    return remapped;
                }
            }
            // Check the next exception
            currentException = ex.getCause();
        }
        // if there is an upwork exception
        return null;
    }
}
