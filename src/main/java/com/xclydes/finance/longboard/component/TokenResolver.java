package com.xclydes.finance.longboard.component;

import com.xclydes.finance.longboard.models.Token;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;

@Slf4j
public class TokenResolver implements org.springframework.graphql.data.method.HandlerMethodArgumentResolver,
                                        graphql.schema.DataFetcher<Token> {

    private static final TypeDescriptor TokenTypeDescriptor = TypeDescriptor.valueOf(Token.class);

    @Override
    public boolean supportsParameter(@NotNull final MethodParameter parameter) {
        boolean capable = false;
        try {
            //Generate a type descriptor for the parameter
            final TypeDescriptor paramTypeDescriptor = new TypeDescriptor(parameter);
            // Does it look like our type?
            capable = paramTypeDescriptor.isAssignableTo(TokenTypeDescriptor);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return capable;
    }

    @Override
    public Object resolveArgument(final MethodParameter parameter,
                                  final DataFetchingEnvironment environment) throws Exception {
        return this.get(environment);
    }

    @Override
    public Token get(final DataFetchingEnvironment environment) throws Exception {
        return environment.getGraphQlContext().get(Token.CtxKey);
    }
}
