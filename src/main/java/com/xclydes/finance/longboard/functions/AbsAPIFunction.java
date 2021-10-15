package com.xclydes.finance.longboard.functions;

import com.xclydes.finance.longboard.apis.IClientProvider;
import org.springframework.messaging.Message;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.function.Function;

public abstract class AbsAPIFunction<C, I, R> extends AbsAPIWrapper<C> implements Function<Message<I>, CorePublisher<R>> {

    public AbsAPIFunction(final IClientProvider<C> clientProvider) {
        super(clientProvider);
    }

    /**
     * Does the actual processing of the request
     * @param sink The publisher to which the outcome of processing
     *             should be published. Just like a promise in Javascript.
     * @param input The content which was submitted as input to this function.
     */
    protected abstract void doProcess(final FluxSink<R> sink, final Message<I> input);

    @Override
    public CorePublisher<R> apply(final Message<I> i) {
        return Flux.<R>create(sink -> doProcess(sink, i))
                .onErrorStop();
    }
}
