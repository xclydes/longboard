package com.xclydes.finance.longboard.functions;

import com.xclydes.finance.longboard.apis.IClientProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.CorePublisher;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbsAPISupplier<C,R> extends AbsAPIConsumer<C> implements Supplier<CorePublisher<R>> {

    public AbsAPISupplier(IClientProvider<C> clientProvider) {
        super(clientProvider);
    }
}
