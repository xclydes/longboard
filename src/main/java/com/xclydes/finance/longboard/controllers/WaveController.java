package com.xclydes.finance.longboard.controllers;

import com.apollographql.apollo.ApolloClient;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.wave.GetUserQuery;
import com.xclydes.finance.longboard.wave.WaveSvc;
import com.xclydes.finance.longboard.wave.fragment.BusinessFragment;
import com.xclydes.finance.longboard.wave.fragment.InvoiceFragment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Optional;

@Controller
public class WaveController extends AbsAPIController<ApolloClient> {

    private final WaveSvc waveSvc;

    public WaveController(final WaveSvc waveSvc) {
        super(waveSvc.getClientProviderGQL());
        this.waveSvc = waveSvc;
    }

    protected WaveSvc getWaveSvc() {
        return waveSvc;
    }

    @QueryMapping
    public Mono<Optional<GetUserQuery.User>> waveUser(final Token token) {
        return getWaveSvc().user(token);
    }

    @QueryMapping
    public Mono<DataPage<BusinessFragment>> waveBusinesses(final Token token,
                                                           @Argument("page") final Integer pageIn,
                                                           @Argument("pageSize") final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().businesses(token, page, pageSize);
    }

    @QueryMapping
    public Mono<Optional<BusinessFragment>> waveBusiness(final Token token,
                                                           @Argument("id") final String id) {
        // Use the resolved parameters
        return getWaveSvc().business(token, id);
    }

    @QueryMapping
    public Mono<DataPage<InvoiceFragment>> waveInvoices(final Token token,
                                                        @Argument("businessId") final String businessID,
                                                        @Argument("ref") final String invoiceRef,
                                                        @Argument("from") final LocalDate from,
                                                        @Argument("to") final LocalDate to,
                                                        @Argument("page") final Integer pageIn,
                                                        @Argument("pageSize") final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().invoices(token, businessID, from, to, page, pageSize, invoiceRef);
    }

}
