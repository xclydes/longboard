package com.xclydes.finance.longboard.wave;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Pagination;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.ArrayUtil;
import com.xclydes.finance.longboard.util.DatesUtil;
import com.xclydes.finance.longboard.util.GraphQLUtil;
import com.xclydes.finance.longboard.util.JsonUtil;
import com.xclydes.finance.longboard.wave.fragment.BusinessFragment;
import com.xclydes.finance.longboard.wave.fragment.CustomerFragment;
import com.xclydes.finance.longboard.wave.fragment.InvoiceFragment;
import com.xclydes.finance.longboard.wave.type.CustomerCreateInput;
import com.xclydes.finance.longboard.wave.type.CustomerPatchInput;
import com.xclydes.finance.longboard.wave.type.CustomerSort;
import com.xclydes.finance.longboard.wave.type.InvoiceCreateInput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.xclydes.finance.longboard.config.CacheConfig.CacheKeys.*;
import static com.xclydes.finance.longboard.util.GraphQLUtil.*;

@Service
@Getter
@Slf4j
public class WaveSvc {

    public static Consumer<ApolloException> ApolloExceptionNoOp = (e) -> {
    };

    public static final String ID_BUSINESS = "Business";
    public static final String ID_PRODUCT = "Product";
    public static final String ID_ACCOUNT = "Account";
    public static final String ID_INVOICE = "Invoice";
    public static final String ID_TRANSACTION = "Transaction";

    public static final DateTimeFormatter inputDateFormat = DatesUtil.formatterSQL();
    public static final DateTimeFormatter reportDateFormat = DatesUtil.formatterReport();

    private final IClientProvider<ApolloClient> clientProviderGQL;
    private final IClientProvider<WebClient> clientProviderRest;
    private final String loginUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final List<String> scopes;
    private final String callback;
    private final Scheduler scheduler;

    public WaveSvc(@Qualifier("wave-graphql") final IClientProvider<ApolloClient> clientProviderGQL,
                   @Qualifier("wave-rest") final IClientProvider<WebClient> clientProviderRest,
                   @Value("${longboard.wave.oauth.login}") final String loginUrl,
                   @Value("${longboard.wave.oauth.token}") final String tokenUrl,
                   @Value("${longboard.wave.client.key}") final String clientId,
                   @Value("${longboard.wave.client.secret}") final String clientSecret,
                   @Value("${longboard.wave.oauth.scopes}") final List<String> scopes,
                   @Value("${longboard.wave.oauth.callback}") final String callback,
                   @Qualifier("longboardScheduler") final Scheduler scheduler) {
        this.clientProviderGQL = clientProviderGQL;
        this.clientProviderRest = clientProviderRest;
        this.loginUrl = loginUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
        this.callback = callback;
        this.scheduler = scheduler;
    }

    protected Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Uses teh graphql client provider to get a client instance authenticated
     * with the token given
     * @param token The authentication token to initialize the client with
     * @return The client instance requested
     */
    public ApolloClient provideGraphQLClient(final Token token) {
        return getClientProviderGQL().getClient(token);
    }

    /**
     * Gets the login URL the user should to gain authorization from Wave
     * @param state The state code to be used for the request
     * @return The request token details
     */
    @Cacheable(cacheNames = {WAVE_OAUTH_URL})
    public Mono<RequestToken> getLoginUrl(final String state) {
        final UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
                .fromHttpUrl(this.getLoginUrl())
                .queryParam("response_type", "code")
                // Add the state attribute
                .queryParam("state", state)
                // Add the client ID
                .queryParam("client_id", this.getClientId())
                // Add the scopes
                .queryParam("scope", String.join(" ", this.getScopes()));
        // If a callback is set
        if (StringUtils.hasText(this.getCallback())) {
            // Add the callback URL
            uriComponentsBuilder.queryParam("redirect_uri", this.getCallback());
        }
        // return the built URL
        return Mono.just(new RequestToken(state, null, uriComponentsBuilder.toUriString()));
    }

    /**
     * Exchanges the request token and verifier supplied for an access token
     * @param verifier The verifier to submit
     * @return The access token returned
     */
    @Cacheable(cacheNames = {WAVE_ACCESSTOKEN})
    public Mono<Token> getAccessToken(final String verifier) {
        // Build the request body
        final MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
        bodyValues.add("grant_type", "authorization_code");
        bodyValues.add("code", verifier);
        // Process it
        return this.exchangeForToken(bodyValues);
    }

    /**
     * Exchanges the request token and verifier supplied for an access token
     * @param refreshToken The refresh token to submit
     * @return The access token returned
     */
    public Mono<Token> getRefreshedAccessToken(final String refreshToken) {
        // Build the request body
        final MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
        bodyValues.add("grant_type", "refresh_token");
        bodyValues.add("refresh_token", refreshToken);
        // Process it
        return this.exchangeForToken(bodyValues);
    }

    private Mono<Token> exchangeForToken(final MultiValueMap<String, String> customValues) {
        // Get the client
        final WebClient restClient = this.getClientProviderRest().getClient();

        // Build the request body
        final MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
        bodyValues.add("client_id", this.getClientId());
        bodyValues.add("client_secret", this.getClientSecret());
        bodyValues.add("redirect_uri", StringUtils.trimWhitespace(this.getCallback()));
        // Add the custom values
        bodyValues.addAll(customValues);

        // Post the oauth2/token/ endpoint
        return restClient.post()
            .uri("oauth2/token/")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(bodyValues))
            .retrieve()
            .bodyToMono(ObjectNode.class)
            .map( jsonNode -> Token.of(
                jsonNode.required("access_token").asText(),
                jsonNode.required("refresh_token").asText(),
                jsonNode.required("expires_in").asInt()
            ));
    }

    /**
     * Decodes and parses the ID given into its constituent parts
     * @param b64Id The base64 ID to be parsed
     * @return A map of attributes to values read from the ID
     */
    @Cacheable(cacheNames = {WAVE_APIID})
    public Map<String, String> decodeId(final String b64Id) {
        final byte[] bytes = Base64Utils.decodeFromString(b64Id);
        final String[] parts = new String(bytes).split("[;:]+");
        return ArrayUtil.zipWithNext(parts);
    }

    /**
     * Encode an ID similar to what Wave would generate give
     * the type and value
     * @param type The type of ID to be generated
     * @param id The value of the ID
     * @return The encoded string as wave would create
     */
    public String encodeId(final String type, final String id) {
        return encodeId(type, id, null);
    }

    /**
     * Similar to {@link #encodeId(String, String)}, but it accepts
     * an additional parameter for the business Id to be encoded as well.
     * @param type The type of ID to be generated
     * @param id The value of the ID
     * @param business the business ID to be encoded, if any
     * @return The encoded string as wave would create
     */
    public String encodeId(final String type, final String id, final String business) {
        String encoded = String.format("%s:%s", type, id);
        if (StringUtils.hasText(business)) {
            encoded += String.format("%s:%s", ID_BUSINESS, business);
        }
        return Base64Utils.encodeToString(encoded.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the user details from wave for the token given.
     * @param token The user token to forward to Wave
     * @return The user details found, if any.
     */
    @Cacheable(cacheNames = {WAVE_USER})
    public Mono<Optional<GetUserQuery.User>> user(final Token token) {
        return processQuery(provideGraphQLClient(token), new GetUserQuery(), (dataResponse) -> {
            // Throw the errors if any
            GraphQLUtil.throwErrors(dataResponse);
            // Otherwise, process the response
            return Optional.ofNullable(dataResponse.getData())
                    .map(GetUserQuery.Data::user);
        });
    }

    /**
     * Gets a page of businesses owned by the token given on Wave
     * @param token The authentication token to be presented
     * @param page The page to be requested
     * @param pageSize The number of elements per page to be requested
     * @return The set matching the configuration specified
     */
    @Cacheable(cacheNames = {WAVE_BUSINESSES})
    public Mono<DataPage<BusinessFragment>> businesses(final Token token, final Integer page, final Integer pageSize) {
        return processQuery(
                provideGraphQLClient(token),
                BusinessListQuery.builder().page(page).pageSize(pageSize).build(),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    // Otherwise, process the response
                    final List<BusinessFragment> businessFragments = Optional
                            .ofNullable(dataResponse.getData())
                            .map(BusinessListQuery.Data::businesses)
                            .map(BusinessListQuery.Businesses::edges)
                            .map(edges -> unwrapNestedElement(edges, BusinessListQuery.Edge::node))
                            .map(nodes -> unwrapNestedElement(nodes, BusinessListQuery.Node::fragments))
                            .map(fragments -> unwrapNestedElement(fragments, BusinessListQuery.Node.Fragments::businessFragment))
                            .orElse(Collections.emptyList());
                    final Pagination pagination = Optional
                            .ofNullable(dataResponse.getData())
                            .map(BusinessListQuery.Data::businesses)
                            .map(BusinessListQuery.Businesses::pageInfo)
                            .map(pageInfo -> new Pagination(pageSize, pageInfo.currentPage(), pageInfo.totalPages(), pageInfo.totalCount()))
                            .orElse(Pagination.UNKNOWN);
                    return new DataPage<>(pagination, businessFragments);
                }
        );
    }

    @Cacheable(cacheNames = {WAVE_BUSINESS})
    public Mono<Optional<BusinessFragment>> business(final Token token, final String businessID) {
        return processQuery(provideGraphQLClient(token), new GetBusinessQuery(businessID), (dataResponse) -> {
            // Throw the errors if any
            GraphQLUtil.throwErrors(dataResponse);
            // Otherwise, process the response
            return Optional.ofNullable(dataResponse.getData())
                    .map(GetBusinessQuery.Data::business)
                    .map(GetBusinessQuery.Business::fragments)
                    .map(GetBusinessQuery.Business.Fragments::businessFragment);
        });
    }

    @Cacheable(cacheNames = {WAVE_INVOICES})
    public Mono<DataPage<InvoiceFragment>> invoices(
            final Token token,
            final String businessID ,
            final LocalDate from,
            final LocalDate to,
            final Integer page,
            final Integer pageSize,
            final String invoiceRef
            ) {
        final LocalDate resolvedFrom = from == null ? LocalDate.EPOCH : from;
        final LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        final int resolvedPage = page == null ? Integer.valueOf(1) : page;
        final int resolvedPageSize = pageSize == null ? Integer.valueOf(99) : pageSize;
        return processQuery(
                provideGraphQLClient(token),
                new GetBusinessInvoicesQuery(businessID,
                    Input.fromNullable(invoiceRef),
                    Input.fromNullable(inputDateFormat.format(resolvedFrom)),
                    Input.fromNullable(inputDateFormat.format(resolvedTo)),
                    Input.fromNullable(resolvedPage),
                    Input.fromNullable(resolvedPageSize)
                ),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    // Otherwise, process the response
                    final List<InvoiceFragment> businessFragments = Optional
                            .ofNullable(dataResponse.getData())
                            .map(GetBusinessInvoicesQuery.Data::business)
                            .map(GetBusinessInvoicesQuery.Business::invoices)
                            .map(GetBusinessInvoicesQuery.Invoices::edges)
                            .map(edges -> unwrapNestedElement(edges, GetBusinessInvoicesQuery.Edge::node))
                            .map(nodes -> unwrapNestedElement(nodes, GetBusinessInvoicesQuery.Node::fragments))
                            .map(fragments -> unwrapNestedElement(fragments, GetBusinessInvoicesQuery.Node.Fragments::invoiceFragment))
                            .orElse(Collections.emptyList());
                    final Pagination pagination = Optional
                            .ofNullable(dataResponse.getData())
                            .map(GetBusinessInvoicesQuery.Data::business)
                            .map(GetBusinessInvoicesQuery.Business::invoices)
                            .map(GetBusinessInvoicesQuery.Invoices::pageInfo)
                            .map(pageInfo -> new Pagination(pageSize, pageInfo.currentPage(), pageInfo.totalPages(), pageInfo.totalCount()))
                            .orElse(Pagination.UNKNOWN);
                    return new DataPage<>(pagination, businessFragments);
                }
        );
    }

    @Cacheable(cacheNames = {WAVE_INVOICE})
    public Mono<Optional<InvoiceFragment>> invoice(final Token token,
                                                  final String businessID,
                                                  final String invoiceID) {
        return processQuery(
                provideGraphQLClient(token),
                new GetBusinessInvoiceQuery(businessID, invoiceID),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    // Otherwise, process the response
                    return Optional
                            .ofNullable(dataResponse.getData())
                            .map(GetBusinessInvoiceQuery.Data::business)
                            .map(GetBusinessInvoiceQuery.Business::invoice)
                            .map(GetBusinessInvoiceQuery.Invoice::fragments)
                            .map(GetBusinessInvoiceQuery.Invoice.Fragments::invoiceFragment);
                }
        );
    }

    @CacheEvict({WAVE_CUSTOMER, WAVE_CUSTOMERS})
    public Flux<CustomerFragment> getBusinessCustomers(final Token token,
                                                            final String businessID) {
        return Flux.create((sink) -> {
            List<CustomerSort> sort = Collections.singletonList(CustomerSort.NAME_ASC);
            final AtomicReference<Integer>  currentPage = new AtomicReference<>(1);
            final AtomicReference<Integer> maxPage = new AtomicReference<>(0);
            do {
                try {
                    final List<GetBusinessCustomersQuery.Edge> edges = processQuery(
                        getClientProviderGQL().getClient(token),
                        GetBusinessCustomersQuery.builder().businessId(businessID)
                                .cusPage(currentPage.get())
                                .cusPageSize(99)
                                .cusSort(sort)
                                .build(),
                        Response::getData
                    )
                    .mapNotNull(GetBusinessCustomersQuery.Data::business)
                    .mapNotNull(business -> business.customers)
                    .mapNotNull(customers -> {
                        final GetBusinessCustomersQuery.PageInfo pageInfo = customers.pageInfo();
                        maxPage.set(Optional.ofNullable(pageInfo.totalPages()).orElse(0));
                        return customers.edges();
                    })
                    .blockOptional()
                    .orElse(Collections.emptyList());
                    // Take the edges found as a stream
                    edges.stream()
                            .map(GetBusinessCustomersQuery.Edge::node)
                            .filter(Objects::nonNull)
                            .map(node -> node.fragments().customerFragment())
                            .forEach(sink::next);
                } catch (Exception e) {
                    sink.error(e);
                }
                currentPage.getAndUpdate(p -> p + 1);
            } while( currentPage.get() < maxPage.get() );
            // That's all
            sink.complete();
        });
    }

    @CacheEvict({WAVE_CUSTOMER, WAVE_CUSTOMERS})
    public Mono<CustomerFragment> saveCustomer(final Token token,
                                               final String businessID,
                                               final CustomerFragment newCustomerFragment) {
        log.info("Saving customer {} ({})  to Wave.", newCustomerFragment.name(), newCustomerFragment.displayId());
        // Determine if there is a customer with the
        return Mono.just(newCustomerFragment)
            .flatMap(customerFragment -> {
                // Find the entry with the same display ID
                return getBusinessCustomers(token, businessID)
                .filter(custFrag -> Objects.equals(custFrag.displayId(), newCustomerFragment.displayId()))
                .next()
                .map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existingCustomerOpt -> existingCustomerOpt
                    .map( existingCust -> {
                        // Parse both notes
                        JsonNode oldNotes = JsonUtil.newObject();
                        try {
                            oldNotes = JsonUtil.reader().readTree(existingCust.internalNotes());
                        }catch (Exception ignored) {}
                        JsonNode newNotes = JsonUtil.newObject();
                        try {
                            newNotes = JsonUtil.reader().readTree(newCustomerFragment.internalNotes());
                        } catch (Exception ignored) {}
                        // Merge both notes
                        final JsonNode mergedNotes = JsonUtil.merge(oldNotes, newNotes);

                        // Merge both records into one
                        final CustomerFragment mergedCustomer = new CustomerFragment(
                                "Customer",
                                existingCust.id(),
                                existingCust.displayId(),
                                newCustomerFragment.firstName(),
                                newCustomerFragment.lastName(),
                                newCustomerFragment.name(),
                                mergedNotes.toPrettyString()
                        );
                        // Perform an update
                        return this.updateCustomer(token, mergedCustomer);
                    })
                    .orElseGet( () -> this.createCustomer(token, businessID, newCustomerFragment) ));
            });
    }

    private Mono<CustomerFragment> createCustomer(final Token token,
                                                  final String business,
                                                  final CustomerFragment customer) {
        // Create the PatchInput
        final CustomerCreateInput input = CustomerCreateInput.builder()
                .displayId(customer.displayId())
                .name(customer.name())
                .firstName(customer.firstName())
                .lastName(customer.lastName())
                .internalNotes(customer.internalNotes())
                .businessId(business)
                .build();
        // Create the mutation
        return processMutation(
            getClientProviderGQL().getClient(token),
            new CreateCustomerMutation(input),
            dataResponse -> {
                // Throw the errors if any
                GraphQLUtil.throwErrors(dataResponse);
                // Get the patch errors
                final CreateCustomerMutation.CustomerCreate created = Optional.ofNullable(dataResponse.getData())
                        .map(data -> data.customerCreate)
                        .orElseThrow(() -> new IllegalStateException("No patch in response"));
                // If it failed
                if(!created.didSucceed && created.inputErrors() != null) {
                    // Process the input errors
                    throw created.inputErrors()
                    .stream().findFirst()
                    .map(error -> new ResponseStatusException(HttpStatus.BAD_REQUEST, error.message()))
                    .orElse(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer creation failed for unknown reasons"));
                }
                // Process the result
                return created.customer().fragments().customerFragment();
            }
        );
    }

    private Mono<CustomerFragment> updateCustomer(final Token token,
                                                  final CustomerFragment customer) {
        log.info("Updating customer {} ({}) : {} to Wave.", customer.name(), customer.displayId(), customer.id());
        // Create the PatchInput
        final CustomerPatchInput input = CustomerPatchInput.builder()
                .id(customer.id())
                .displayId(customer.displayId())
                .name(customer.name())
                .firstName(customer.firstName())
                .lastName(customer.lastName())
                .internalNotes(customer.internalNotes())
                .build();
        // Create the mutation
        return processMutation(
            getClientProviderGQL().getClient(token),
            new PatchCustomerMutation(input),
            dataResponse -> {
                // Throw the errors if any
                GraphQLUtil.throwErrors(dataResponse);
                // Get the patch errors
                final PatchCustomerMutation.CustomerPatch patched = Optional.ofNullable(dataResponse.getData())
                        .map(data -> data.customerPatch)
                        .orElseThrow(() -> new IllegalStateException("No patch in response"));
                // If it failed
                if(!patched.didSucceed && patched.inputErrors() != null) {
                    // Process the input errors
                    throw patched.inputErrors()
                    .stream().findFirst()
                    .map(error -> new ResponseStatusException(HttpStatus.BAD_REQUEST, error.message()))
                    .orElse(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer update failed for unknown reasons"));
                }
                // Process the result
                return patched.customer().fragments().customerFragment();
            }
        );
    }

    @CacheEvict({WAVE_INVOICE, WAVE_INVOICES})
    public Mono<CreateInvoiceMutation.InvoiceCreate> createInvoice(final InvoiceCreateInput invoice) {
//        val mutationResult = clientGraphQL.mutate(CreateInvoiceMutation(invoice)).await()
//        mutationResult.data?.invoiceCreate
        return Mono.empty();
    }

}
