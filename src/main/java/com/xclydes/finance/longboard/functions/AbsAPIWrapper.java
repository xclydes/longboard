package com.xclydes.finance.longboard.functions;

import com.xclydes.finance.longboard.apis.IClientProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Predicate;

public abstract class AbsAPIWrapper<C> {

    public static Predicate<String> PREDICATE_NONEMPTY = s -> StringUtils.hasText(StringUtils.trimWhitespace(s));

    private final IClientProvider<C> clientProvider;

    public AbsAPIWrapper(IClientProvider<C> clientProvider) {
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
}
