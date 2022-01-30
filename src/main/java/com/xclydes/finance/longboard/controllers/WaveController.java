package com.xclydes.finance.longboard.controllers;

import com.apollographql.apollo.ApolloClient;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Pagination;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.models.Team;
import com.xclydes.finance.longboard.wave.GetUserQuery;
import com.xclydes.finance.longboard.wave.WaveSvc;
import com.xclydes.finance.longboard.wave.fragment.*;
import com.xclydes.finance.longboard.wave.models.InvoiceInput;
import com.xclydes.finance.longboard.wave.models.InvoicePayment;
import com.xclydes.finance.longboard.wave.type.AccountSubtypeValue;
import com.xclydes.finance.longboard.wave.type.AccountTypeValue;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public Publisher<GetUserQuery.User> waveUser(final Token token) {
        return getWaveSvc().getUser(token);
    }

    @QueryMapping
    public Publisher<DataPage<BusinessFragment>> waveBusinesses(final Token token,
                                                           @Argument("page") final Integer pageIn,
                                                           @Argument("pageSize") final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().getBusinessesPage(token, page, pageSize);
    }

    @QueryMapping
    public Publisher<CustomerFragment> waveCustomer(final Token token,
                                           @Argument("businessId") final String businessID,
                                           @Argument("id") final String customerID) {
        // TODO Look it up
//        return getWaveSvc().getCustomersPage(token, businessID, page, pageSize);
        return Mono.empty();
    }

    @QueryMapping
    public Publisher<DataPage<CustomerFragment>> waveCustomers(final Token token,
                                           @Argument("businessId") final String businessID,
                                           @Argument("page") final Integer pageIn,
                                           @Argument("pageSize") final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().getCustomersPage(token, businessID, page, pageSize);
    }

    @QueryMapping
    public Publisher<ProductFragment> waveProduct(final Token token,
                                                  @Argument("businessId") final String businessID,
                                                  @Argument("id") final String productID) {
        return getWaveSvc().getProduct(token, businessID, productID);
    }

    @QueryMapping
    public Publisher<DataPage<ProductFragment>> waveProducts(final Token token,
                                           @Argument("businessId") final String businessID,
                                           @Argument("page") final Integer pageIn,
                                           @Argument("pageSize") final Integer pageSizeIn) {
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().getProductsPage(token, businessID, page, pageSize);
    }

    @QueryMapping
    public Publisher<DataPage<AccountFragment>> waveAccounts(final Token token,
                         @Argument("businessId") final String businessID,
                         @Argument("page") final Integer pageIn,
                         @Argument("pageSize") final Integer pageSizeIn,
                         @Argument("types") final List<String> typeNames,
                         @Argument("subTypes") final List<String> subtypeNames
    ) {
        final List<AccountTypeValue> types = Optional.ofNullable(typeNames)
                .orElse(Collections.emptyList())
                .stream().map(AccountTypeValue::valueOf)
                .collect(Collectors.toList());
        final List<AccountSubtypeValue> subtypes = Optional.ofNullable(subtypeNames)
                .orElse(Collections.emptyList())
                .stream().map( AccountSubtypeValue::valueOf)
                .collect(Collectors.toList());
        // Transform the parameters to their defaults
        final Integer page = pageIn != null ? pageIn : 1;
        final Integer pageSize = pageSizeIn != null ? pageSizeIn : 99;
        // Use the resolved parameters
        return getWaveSvc().getAccountsPage(token, businessID, page, pageSize, types, subtypes);
    }

    @QueryMapping
    public Publisher<BusinessFragment> waveBusiness(final Token token,
                                                           @Argument("id") final String id) {
        // Use the resolved parameters
        return getWaveSvc().getBusiness(token, id);
    }

    @QueryMapping
    public Publisher<DataPage<InvoiceFragment>> waveInvoices(final Token token,
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
        return getWaveSvc().getInvoicesPage(token, businessID, from, to, page, pageSize, invoiceRef);
    }

    @MutationMapping
    public Publisher<DataPage<CustomerFragment>> waveSaveCustomers(final Token token,
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

    @MutationMapping
    public Flux<InvoiceFragment> waveSaveInvoices(final Token token,
                                                              @Argument("business") final String businessID,
                                                              @Argument("invoices") final List<InvoiceInput> inputs) {
        //Process each team input
        return Flux.fromIterable(inputs)
        .parallel()
        .runOn(getTaskScheduler())
        .flatMap(transaction -> getWaveSvc().saveInvoice(token, transaction, businessID))
        .sequential();
    }

    @MutationMapping
    public Flux<String> wavePayInvoices(final Token token,
                                          @Argument("payments") final List<InvoicePayment> inputs) {
        //Process each team input
        return Flux.fromIterable(inputs)
        .parallel()
        .runOn(getTaskScheduler())
        .flatMap(payment -> getWaveSvc().payInvoice(token, payment))
        .map(jsonNode -> jsonNode != null ? jsonNode.required("id").textValue() : null)
        .sequential();
    }
}
