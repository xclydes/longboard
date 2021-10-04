package com.xclydes.finance.longboard.functions;

import com.xclydes.finance.longboard.apis.IClientProvider;
import reactor.core.CorePublisher;

import java.util.function.Function;

public abstract class AbsAPIFunction<C, I, R> extends AbsAPIConsumer<C> implements Function<I, CorePublisher<R>> {

    public AbsAPIFunction(final IClientProvider<C> clientProvider) {
        super(clientProvider);
    }
}
