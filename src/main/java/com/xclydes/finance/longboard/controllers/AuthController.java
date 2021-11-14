package com.xclydes.finance.longboard.controllers;

import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.UpworkSvc;
import com.xclydes.finance.longboard.wave.WaveSvc;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.CorePublisher;
import reactor.core.publisher.Mono;

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
    public Mono<RequestToken> upworkLogin(@Argument("state") final String state) {
        return Mono.just(getUpworkSvc().startLogin(state));
    }

    @QueryMapping
    public CorePublisher<Token> upworkAccessToken(@Argument("verifier") final String verifier) {
        return Mono.just(getUpworkSvc().getAccessToken(verifier));
    }

    @QueryMapping
    public CorePublisher<Token> upworkRefreshToken(@Argument("refresh") final String refreshCode) {
        return Mono.just(getUpworkSvc().getRefreshedAccessToken(refreshCode));
    }

    @QueryMapping
    public CorePublisher<RequestToken> waveLogin(@Argument("state") final String state) {
        return getWaveSvc().getLoginUrl(state);
    }

    @QueryMapping
    public CorePublisher<Token> waveAccessToken(@Argument("verifier") final String verifier) {
        return getWaveSvc().getAccessToken(verifier);
    }

    @QueryMapping
    public CorePublisher<Token> waveRefreshToken(@Argument("refresh") final String refreshCode) {
        return getWaveSvc().getRefreshedAccessToken(refreshCode);
    }

    @GetMapping("/auth/oauth2/callback")
    public CorePublisher<ResponseEntity<String>> waveCallback(@RequestParam("code") String code) {
        return Mono.just(ResponseEntity.ok("Your verifier is " + code));
    }
}
