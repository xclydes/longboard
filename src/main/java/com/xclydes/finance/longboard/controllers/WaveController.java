package com.xclydes.finance.longboard.controllers;

import com.apollographql.apollo.ApolloClient;
import com.xclydes.finance.longboard.models.FragmentPage;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.svc.WaveSvc;
import com.xclydes.finance.longboard.wave.fragment.BusinessFragment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import reactor.core.publisher.Mono;

@Controller
public class WaveController extends AbsAPIController<ApolloClient> {

    public static final String TOKEN_HEADER_NAME = "x-proxy-authorization";

    private final WaveSvc waveSvc;

    public WaveController(final WaveSvc waveSvc) {
        super(waveSvc.getClientProviderGQL());
        this.waveSvc = waveSvc;
    }

    protected WaveSvc getWaveSvc() {
        return waveSvc;
    }

    private Token requiresToken(final String input) {
        return Token.of(requires(input, StringUtils::hasText, String.format("A valid token is required via the '%s' header", TOKEN_HEADER_NAME)));
    }

    @QueryMapping
    public Mono<RequestToken> waveLogin(@Argument final String state) {
        return getWaveSvc().getLoginUrl(state);
    }

    @QueryMapping
    public Mono<FragmentPage<BusinessFragment>> waveBusinesses(@RequestHeader(TOKEN_HEADER_NAME) final String token,
                                                               @Argument final Integer pageIn, @Argument final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().businesses(
                requiresToken(token),
                page,
                pageSize
        );
    }
}
