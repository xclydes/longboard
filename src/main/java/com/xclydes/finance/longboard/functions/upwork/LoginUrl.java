package com.xclydes.finance.longboard.functions.upwork;

import com.Upwork.api.OAuthClient;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.functions.AbsAPISupplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Qualifier("upwork")
public class LoginUrl extends AbsAPISupplier<OAuthClient, String> {

    private final String callbackUrl;

    public LoginUrl(@Qualifier("upwork") final IClientProvider<OAuthClient> clientProvider,
                    @Value("${longboard.upwork.client.callback}") final String callbackUrl) {
        super(clientProvider);
        this.callbackUrl = callbackUrl;
    }

    @Override
    public Mono<String> get() {
        return Mono.just(getClientProvider().getClient().getAuthorizationUrl( this.callbackUrl ));
    }
}
