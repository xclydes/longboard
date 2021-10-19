package com.xclydes.finance.longboard.util;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Predicate;

public class ValidationUtil {
    public static Predicate<String> PREDICATE_NONEMPTY = s -> StringUtils.hasText(StringUtils.trimWhitespace(s));

    protected static <T> T requires(T value, Predicate<T> checker) {
        return requires(value, checker, null);
    }

    public static <T> T requires(T value, Predicate<T> checker, final String msg) {
        if(!checker.test(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        return value;
    }
}
