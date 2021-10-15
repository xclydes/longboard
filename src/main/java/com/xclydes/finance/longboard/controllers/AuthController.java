package com.xclydes.finance.longboard.controllers;

import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.svc.UpworkSvc;
import com.xclydes.finance.longboard.svc.WaveSvc;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.CorePublisher;
import reactor.core.publisher.Mono;

import static com.xclydes.finance.longboard.config.CacheConfig.WAVE_OAUTH_URL;

@Controller
public class AuthController {

    private final UpworkSvc upworkSvc;
    private final WaveSvc waveSvc;


    public AuthController(final UpworkSvc upworkSvc,
                          final WaveSvc waveSvc
                            ) {
        this.upworkSvc = upworkSvc;
        this.waveSvc = waveSvc;
    }

    public WaveSvc getWaveSvc() {
        return waveSvc;
    }

    public UpworkSvc getUpworkSvc() {
        return upworkSvc;
    }

    @QueryMapping
    public Mono<RequestToken> upworkLogin() {
        return Mono.just(getUpworkSvc().startLogin());
    }

    @QueryMapping
    public CorePublisher<Token> upworkAccessToken(@Argument final String verifier,
                                                  @Argument final Token token) {
        return Mono.just(getUpworkSvc().getAccessToken(token, verifier));
    }


    @QueryMapping
    @Cacheable(cacheNames = {WAVE_OAUTH_URL})
    public Mono<RequestToken> waveLogin(@Argument final String state) {
        return getWaveSvc().getLoginUrl(state);
    }
}
