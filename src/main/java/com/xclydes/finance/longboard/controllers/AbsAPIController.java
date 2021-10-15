package com.xclydes.finance.longboard.controllers;

import com.xclydes.finance.longboard.apis.IClientProvider;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.function.Consumer;
import java.util.function.Predicate;

@Getter
public abstract class AbsAPIController<C> {

    public static Predicate<String> PREDICATE_NONEMPTY = s -> StringUtils.hasText(StringUtils.trimWhitespace(s));

    private final IClientProvider<C> clientProvider;

    protected AbsAPIController(final IClientProvider<C> clientProvider) {
        this.clientProvider = clientProvider;
    }

    public IClientProvider<C> getClientProvider() {
        return clientProvider;
    }

    protected static <T> T requires(T value, Predicate<T> checker) {
        return requires(value, checker, null);
    }

    protected static <T> T requires(T value, Predicate<T> checker, final String msg) {
        if(!checker.test(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        return value;
    }

    protected static <T> Mono<T> wrapLogic(final Consumer<MonoSink<T>> consumer) {
        return Mono.create(consumer).onErrorStop();
    }
}
