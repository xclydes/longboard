package com.xclydes.finance.longboard.functions;

import com.xclydes.finance.longboard.apis.IClientProvider;
import reactor.core.CorePublisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.function.Supplier;

public abstract class AbsAPISupplier<C,R> extends AbsAPIWrapper<C> implements Supplier<CorePublisher<R>> {

    public AbsAPISupplier(IClientProvider<C> clientProvider) {
        super(clientProvider);
    }

    /**
     * Does the actual processing of the request
     * @param sink The publisher to which the outcome of processing
     *             should be published. Just like a promise in Javascript.
     */
    protected abstract void doProcess(final MonoSink<R> sink);

    @Override
    public CorePublisher<R> get() {
        return Mono.create(this::doProcess)
                .onErrorStop();
    }

}
