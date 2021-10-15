package com.xclydes.finance.longboard.apis;


import com.xclydes.finance.longboard.models.Token;

@FunctionalInterface
public interface IClientProvider<C> {


    default C getClient() {
        return getClient(Token.EMPTY);
    }

    C getClient(final Token token);
}
