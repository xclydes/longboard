package com.xclydes.finance.longboard.controllers;

import com.apollographql.apollo.ApolloClient;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Pagination;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.models.Team;
import com.xclydes.finance.longboard.wave.GetUserQuery;
import com.xclydes.finance.longboard.wave.WaveSvc;
import com.xclydes.finance.longboard.wave.fragment.BusinessFragment;
import com.xclydes.finance.longboard.wave.fragment.CustomerFragment;
import com.xclydes.finance.longboard.wave.fragment.InvoiceFragment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@Slf4j
public class WaveController extends AbsAPIController<ApolloClient> {

    private final WaveSvc waveSvc;
    private final ConversionService conversionService;
    private final Scheduler taskScheduler;

    public WaveController(final WaveSvc waveSvc,
                          @Qualifier("longboardScheduler") final Scheduler scheduler,
                          final ConversionService conversionService) {
        super(waveSvc.getClientProviderGQL());
        this.waveSvc = waveSvc;
        this.taskScheduler = scheduler;
        this.conversionService = conversionService;
    }

    protected Scheduler getTaskScheduler() {
        return taskScheduler;
    }

    protected ConversionService getConversionService() {
        return conversionService;
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

    @MutationMapping
    public Mono<DataPage<CustomerFragment>> waveSaveCustomers(final Token token,
                                                              @Argument("business") final String businessID,
                                                              @Argument("customers") final List<Team> inputs) {
        //Process each team input
        return Flux.fromIterable(inputs)
        .parallel()
        .runOn(getTaskScheduler())
        .flatMap(team -> {
            final CustomerFragment fragment = getConversionService().convert(team, CustomerFragment.class);
            return getWaveSvc().saveCustomer(token, businessID, fragment);
        })
        .map(Optional::ofNullable)
        // Collect the flux content as a list
        .reduce(ArrayList<CustomerFragment>::new, (list, customerFragment2) -> {
            customerFragment2.ifPresent(list::add);
            return list;
        })
        .reduce((f1, f2) -> {
            f1.addAll(f2);
            return f1;
        })
        // Convert the list to a DataPage
        .map(customerFragments -> {
            log.info("Fragments: {}", customerFragments);
            // Define the pagination
            final Pagination pagination = new Pagination(customerFragments.size());
            // Define the data page
            return new DataPage<>(pagination, customerFragments);
        });
    }
}
