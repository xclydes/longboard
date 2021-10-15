package com.xclydes.finance.longboard.apis;


@FunctionalInterface
public interface IClientProvider<C> {


    default C getClient() {
        return getClient(Token.EMPTY);
    }

    C getClient(final Token token);
}
