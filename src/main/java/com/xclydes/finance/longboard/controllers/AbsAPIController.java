package com.xclydes.finance.longboard.controllers;

import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.ValidationUtil;
import lombok.Getter;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.function.Consumer;

@Getter
public abstract class AbsAPIController<C> {

    public static final String TOKEN_HEADER_NAME = "x-proxy-authorization";

    private final IClientProvider<C> clientProvider;

    protected AbsAPIController(final IClientProvider<C> clientProvider) {
        this.clientProvider = clientProvider;
    }

    public IClientProvider<C> getClientProvider() {
        return clientProvider;
    }

    public Token requiresToken(final String input) {
        return Token.of(ValidationUtil.requires(input, StringUtils::hasText, String.format("A valid token is required via the '%s' header", TOKEN_HEADER_NAME)));
    }

    public static <T> Mono<T> wrapLogic(final Consumer<MonoSink<T>> consumer) {
        return Mono.create(consumer).onErrorStop();
    }
}
