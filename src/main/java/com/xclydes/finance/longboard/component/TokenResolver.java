package com.xclydes.finance.longboard.component;

import com.xclydes.finance.longboard.models.Token;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class TokenResolver implements org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver,
                                        org.springframework.graphql.data.method.HandlerMethodArgumentResolver,
                                        org.springframework.web.method.support.HandlerMethodArgumentResolver,
                                        graphql.schema.DataFetcher<Token> {

    public static final String HEADER_KEY = "x-token-key";
    public static final String HEADER_SECRET = "x-token-secret";

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
    public Object resolveArgument(final MethodParameter parameter,
                                  final ModelAndViewContainer mavContainer,
                                  final NativeWebRequest webRequest,
                                  final WebDataBinderFactory binderFactory) {
        final String key = webRequest.getHeader(HEADER_KEY);
        final String secret = webRequest.getHeader(HEADER_SECRET);
        return Token.of(key, secret);
    }

    @NotNull
    @Override
    public Mono<Object> resolveArgument(final MethodParameter parameter,
                                        final BindingContext bindingContext,
                                        final ServerWebExchange exchange) {
        return Mono.create(sink -> {
            // Get the incoming request
            final ServerHttpRequest request = exchange.getRequest();
            // Get the headers from the request
            final HttpHeaders headers = request.getHeaders();
            // Get the key
            final String key = headers.getFirst(HEADER_KEY);
            // Get the optional secret
            final String secret = headers.getFirst(HEADER_SECRET);
            // Formulate a token
            final Token token = Token.of(key, secret);
            // Complete with that token
            sink.success(token);
        });
    }

    @Override
    public Token get(final DataFetchingEnvironment environment) throws Exception {
        final GraphQLContext context =  environment.getGraphQlContext();
        final reactor.util.context.Context ctx = context.get("org.springframework.graphql.execution.ReactorContextManager.CONTEXT_VIEW");
        final ServerWebExchange exchange = ctx.get(ServerWebExchange.class);
        final ServerHttpRequest request = exchange.getRequest();
        final HttpHeaders headers = request.getHeaders();
        // Get the key
        final String key = headers.getFirst(HEADER_KEY);
        // Get the optional secret
        final String secret = headers.getFirst(HEADER_SECRET);
        // Formulate a token
        return Token.of(key, secret);
    }
}
