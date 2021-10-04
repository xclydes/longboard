package com.xclydes.finance.longboard.apis;


import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface IClientProvider<C> {


    default C getClient() {
        return getClient(Token.EMPTY);
    }

    C getClient(final Token token);
}
