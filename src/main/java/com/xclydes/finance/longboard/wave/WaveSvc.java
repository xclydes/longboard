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
import com.xclydes.finance.longboard.wave.fragment.*;
import com.xclydes.finance.longboard.wave.models.InvoiceInput;
import com.xclydes.finance.longboard.wave.models.InvoicePayment;
import com.xclydes.finance.longboard.wave.type.*;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    @Cacheable(cacheNames = {WAVE_OAUTH_URL}, keyGenerator = "longboardWaveCacheKeyGen")
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
    @Cacheable(cacheNames = {WAVE_ACCESSTOKEN}, keyGenerator = "longboardWaveCacheKeyGen")
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
    @Cacheable(cacheNames = {WAVE_APIID}, keyGenerator = "longboardWaveCacheKeyGen")
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
    @Cacheable(cacheNames = {WAVE_USER}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<GetUserQuery.User> getUser(final Token token) {
        return processQuery(provideGraphQLClient(token), new GetUserQuery(), (dataResponse) -> {
            // Throw the errors if any
            GraphQLUtil.throwErrors(dataResponse);
            // Otherwise, process the response
            return Optional.ofNullable(dataResponse.getData())
                    .map(GetUserQuery.Data::user)
                    .orElse(null);
        });
    }

    /**
     * Gets a page of businesses owned by the token given on Wave
     * @param token The authentication token to be presented
     * @param page The page to be requested
     * @param pageSize The number of elements per page to be requested
     * @return The set matching the configuration specified
     */
    @Cacheable(cacheNames = {WAVE_BUSINESSES}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<DataPage<BusinessFragment>> getBusinessesPage(final Token token, final Integer page, final Integer pageSize) {
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

    @Cacheable(cacheNames = {WAVE_BUSINESS}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<BusinessFragment> getBusiness(final Token token, final String businessID) {
        return processQuery(provideGraphQLClient(token), new GetBusinessQuery(businessID), (dataResponse) -> {
            // Throw the errors if any
            GraphQLUtil.throwErrors(dataResponse);
            // Otherwise, process the response
            return Optional.ofNullable(dataResponse.getData())
                    .map(GetBusinessQuery.Data::business)
                    .map(GetBusinessQuery.Business::fragments)
                    .map(GetBusinessQuery.Business.Fragments::businessFragment)
                    .orElse(null);
        });
    }

    @Cacheable(cacheNames = {WAVE_INVOICE}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<InvoiceFragment> getInvoice(final Token token,
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
                            .map(GetBusinessInvoiceQuery.Invoice.Fragments::invoiceFragment)
                            .orElse(null);
                }
        );
    }

    @CacheEvict(cacheNames = {WAVE_CUSTOMERS}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<DataPage<CustomerFragment>> getCustomersPage(final Token token,
                                                  final String businessID,
                                                   final Integer page, final Integer pageSize) {
        final List<CustomerSort> sort = Collections.singletonList(CustomerSort.NAME_ASC);
        return processQuery(
                provideGraphQLClient(token),
                GetBusinessCustomersQuery.builder().businessId(businessID)
                        .cusPage(page)
                        .cusPageSize(pageSize)
                        .cusSort(sort)
                        .build(),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    final GetBusinessCustomersQuery.Data data = dataResponse.getData();
                    final Optional<GetBusinessCustomersQuery.Customers> customersOpt = Optional
                            .ofNullable(data)
                            .map(GetBusinessCustomersQuery.Data::business)
                            .map(GetBusinessCustomersQuery.Business::customers);
                    // Otherwise, process the response
                    final List<CustomerFragment> businessFragments = customersOpt
                            .map(GetBusinessCustomersQuery.Customers::edges)
                            .map(edges -> unwrapNestedElement(edges, GetBusinessCustomersQuery.Edge::node))
                            .map(nodes -> unwrapNestedElement(nodes, GetBusinessCustomersQuery.Node::fragments))
                            .map(fragments -> unwrapNestedElement(fragments, GetBusinessCustomersQuery.Node.Fragments::customerFragment))
                            .orElse(Collections.emptyList());
                    final Pagination pagination = customersOpt
                            .map(GetBusinessCustomersQuery.Customers::pageInfo)
                            .map(pageInfo -> new Pagination(pageSize, pageInfo.currentPage(), pageInfo.totalPages(), pageInfo.totalCount()))
                            .orElse(Pagination.UNKNOWN);
                    return new DataPage<>(pagination, businessFragments);
                }
        );
    }

    @CacheEvict(cacheNames = {WAVE_CUSTOMERS}, keyGenerator = "longboardWaveCacheKeyGen")
    public Flux<CustomerFragment> getAllCustomers(final Token token,
                                                  final String businessID) {
        final List<CustomerSort> sort = Collections.singletonList(CustomerSort.NAME_ASC);
        return getAll(token, (gQLClient, currentPage, maxPages) -> {
            final List<GetBusinessCustomersQuery.Edge> edges = processQuery(
                    getClientProviderGQL().getClient(token),
                    GetBusinessCustomersQuery.builder().businessId(businessID)
                            .cusPage(currentPage)
                            .cusPageSize(99)
                            .cusSort(sort)
                            .build(),
                    Response::getData
            )
                    .mapNotNull(GetBusinessCustomersQuery.Data::business)
                    .mapNotNull(business -> business.customers)
                    .mapNotNull(customers -> {
                        final GetBusinessCustomersQuery.PageInfo pageInfo = customers.pageInfo();
                        maxPages.set(Optional.ofNullable(pageInfo.totalPages()).orElse(0));
                        return customers.edges();
                    })
                    .blockOptional()
                    .orElse(Collections.emptyList());
            // Take the edges found as a stream
            return edges.stream()
                    .map(GetBusinessCustomersQuery.Edge::node)
                    .filter(Objects::nonNull)
                    .map(node -> node.fragments().customerFragment())
                    .collect(Collectors.toList());
        });
    }

    @CacheEvict(cacheNames = {WAVE_CUSTOMER, WAVE_CUSTOMERS})
    public Mono<CustomerFragment> saveCustomer(final Token token,
                                               final String businessID,
                                               final CustomerFragment newCustomerFragment) {
        log.info("Saving customer {} ({})  to Wave.", newCustomerFragment.name(), newCustomerFragment.displayId());
        // Determine if there is a customer with the
        return Mono.just(newCustomerFragment)
            .flatMap(customerFragment -> {
                // Find the entry with the same display ID
                return getAllCustomers(token, businessID)
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

    /* Start Invoices */
    @Cacheable(cacheNames = {WAVE_INVOICES_PAGE}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<DataPage<InvoiceFragment>> getInvoicesPage(
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

    @Cacheable(cacheNames = {WAVE_INVOICES}, keyGenerator = "longboardWaveCacheKeyGen")
    public Flux<InvoiceFragment> getAllInvoices(
            final Token token,
            final String businessID,
            final LocalDate fromIn,
            final LocalDate toIn,
            final String invoiceRef
    ) {
        final LocalDate from = Optional.ofNullable(fromIn).orElse(LocalDate.EPOCH);
        final LocalDate to = Optional.ofNullable(toIn).orElse(LocalDate.now());
        return getAll(token, (gQLClient, currentPage, maxPages) -> {
            final List<GetBusinessInvoicesQuery.Edge> edges = processQuery(
                    gQLClient,
                    GetBusinessInvoicesQuery.builder()
                            .businessId(businessID)
                            .filterFrom(inputDateFormat.format(from))
                            .filterTo(inputDateFormat.format(to))
                            .filterNum(invoiceRef)
                            .build(),
                    Response::getData
            )
                    .mapNotNull(GetBusinessInvoicesQuery.Data::business)
                    .mapNotNull(business -> business.invoices)
                    .mapNotNull(products -> {
                        final GetBusinessInvoicesQuery.PageInfo pageInfo = products.pageInfo();
                        maxPages.set(Optional.ofNullable(pageInfo.totalPages()).orElse(0));
                        return products.edges();
                    })
                    .blockOptional().orElse(Collections.emptyList());
            // Take the edges found as a stream
            return edges.stream()
                    .map(GetBusinessInvoicesQuery.Edge::node)
                    .map(node -> node.fragments().invoiceFragment())
                    .collect(Collectors.toList());
        });
    }

    public Mono<ObjectNode> payInvoice(
            final Token token,
            final InvoicePayment payment
            ) {
        // Decode the invoice ID
        final Map<String, String> decodedInvoiceID = decodeId(payment.getInvoiceID());
        // Create the account reference
        final ObjectNode acctRef = JsonUtil.newObject().put("id", payment.getAccountClassicID());
        final String url = String.format(
            "/businesses/%s/invoices/%s/payments/",
                decodedInvoiceID.get(ID_BUSINESS),
                decodedInvoiceID.get(ID_INVOICE)
        );
        final WebClient restClient = getClientProviderRest().getClient(token);
        final ObjectNode jsonBody = JsonUtil.newObject()
                .put("amount", payment.getAmount())
                .put("exchange_rate", 1)
                .put("memo", payment.getMemo())
                .putPOJO("payment_account", acctRef)
                .put("payment_date", inputDateFormat.format(payment.getDate()))
                .put("payment_method", payment.getMethod().toString());
        // Make the request
        final WebClient.ResponseSpec request = restClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve();
        return request
                .toEntity(ObjectNode.class)
                .mapNotNull(entity -> {
                    ObjectNode body = null;
                    if(entity.getStatusCode().is2xxSuccessful()) {
                        body = entity.getBody();
                    }
                    return body;
                });
    }
    /* End Invoices */

    @CacheEvict(cacheNames = {WAVE_INVOICE, WAVE_INVOICES, WAVE_INVOICES_PAGE}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<InvoiceFragment> saveInvoice(
            final Token token,
            final InvoiceInput transactionWrapper,
            final String businessID
        ) {
        // Convert the line items
        final List<InvoiceCreateItemInput> items = transactionWrapper.lineItems.stream()
                .map(item ->
                        InvoiceCreateItemInput.builder()
                                .productId(item.getProductID())
                                .description(item.getDescription())
                                // Force 5 decimals to control calculations
                                .quantity(BigDecimal.valueOf(item.getQuantity()).setScale(5, RoundingMode.HALF_UP).toString())
                                .unitPrice(BigDecimal.valueOf(item.getUnitPrice()).setScale(5, RoundingMode.HALF_UP).toString())
                                .build()
                )
                .collect(Collectors.toList());
        // Create the input
        final InvoiceCreateInput invoiceInput = InvoiceCreateInput.builder()
                .businessId(businessID)
                .customerId(transactionWrapper.getCustomerID())
                .status(InvoiceCreateStatus.SAVED)
                // TODO Use one from the input
                .currency(CurrencyCode.USD)
                .invoiceDate(inputDateFormat.format(transactionWrapper.getDatePosted()))
                .title(transactionWrapper.getTitle())
                .invoiceNumber(transactionWrapper.getReference())
                .dueDate(inputDateFormat.format(transactionWrapper.getDateDue()))
                .items(items)
                .memo(transactionWrapper.getMemo())
                .build();
        return processMutation(
            getClientProviderGQL().getClient(token),
            CreateInvoiceMutation.builder().input(invoiceInput).build(),
            dataResponse -> {
                // Throw the errors if any
                GraphQLUtil.throwErrors(dataResponse);
                // Otherwise, process the response
                return Optional
                        .ofNullable(dataResponse.getData())
                        .map(CreateInvoiceMutation.Data::invoiceCreate)
                        .orElse(null);
            }
        )
        .flatMap(invoiceCreate -> {
            final Mono<InvoiceFragment> out;
            if(!invoiceCreate.didSucceed) {
                out = Mono.error(Objects.requireNonNull(invoiceCreate.inputErrors())
                    .stream()
                    .findFirst()
                    .map(error -> new APIException(error.message(), "Rejected", error.code))
                    .orElse(new APIException("Invoice creation failed", "Unknown", "400")));
            } else {
                // Lookup the invoice
                out = getInvoice(token, businessID, invoiceCreate.invoice.fragments().invoiceFragment().id());
            }
            return out;
        });

    }

    /* Start Products */
    @Cacheable(cacheNames = {WAVE_PRODUCT}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<ProductFragment> getProduct(
        final Token token,
        final String businessID,
        final String productID
    ){
        return processQuery(
            getClientProviderGQL().getClient(token),
            GetBusinessProductQuery.builder()
                .businessId(businessID)
                .prodId(productID)
                .build(),
            dataResponse -> {
                // Throw the errors if any
                GraphQLUtil.throwErrors(dataResponse);
                // Otherwise, process the response
                return Optional
                    .ofNullable(dataResponse.getData())
                    .map(GetBusinessProductQuery.Data::business)
                    .map(GetBusinessProductQuery.Business::product)
                    .map(GetBusinessProductQuery.Product::fragments)
                    .map(GetBusinessProductQuery.Product.Fragments::productFragment)
                    .orElse(null);
            }
        );
    }

    @CacheEvict(cacheNames = {WAVE_CUSTOMERS}, keyGenerator = "longboardWaveCacheKeyGen")
    public Mono<DataPage<ProductFragment>> getProductsPage(final Token token,
                                                             final String businessID,
                                                             final Integer page,
                                                           final Integer pageSize) {
        return processQuery(
                provideGraphQLClient(token),
                GetBusinessProductsQuery.builder()
                        .businessId(businessID)
                        .prodPage(page)
                        .prodPageSize(pageSize)
                        .build(),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    final GetBusinessProductsQuery.Data data = dataResponse.getData();
                    final Optional<GetBusinessProductsQuery.Products> customersOpt = Optional
                            .ofNullable(data)
                            .map(GetBusinessProductsQuery.Data::business)
                            .map(GetBusinessProductsQuery.Business::products);
                    // Otherwise, process the response
                    final List<ProductFragment> businessFragments = customersOpt
                            .map(GetBusinessProductsQuery.Products::edges)
                            .map(edges -> unwrapNestedElement(edges, GetBusinessProductsQuery.Edge::node))
                            .map(nodes -> unwrapNestedElement(nodes, GetBusinessProductsQuery.Node::fragments))
                            .map(fragments -> unwrapNestedElement(fragments, GetBusinessProductsQuery.Node.Fragments::productFragment))
                            .orElse(Collections.emptyList());
                    final Pagination pagination = customersOpt
                            .map(GetBusinessProductsQuery.Products::pageInfo)
                            .map(pageInfo -> new Pagination(pageSize, pageInfo.currentPage(), pageInfo.totalPages(), pageInfo.totalCount()))
                            .orElse(Pagination.UNKNOWN);
                    return new DataPage<>(pagination, businessFragments);
                }
        );
    }

    public Flux<ProductFragment> getAllProducts(
            final Token token,
            final String businessID
    ){
        return getAll(token, (gQLClient, currentPage, maxPages) -> {
            final List<GetBusinessProductsQuery.Edge> edges = processQuery(
                    gQLClient,
                    GetBusinessProductsQuery.builder()
                            .businessId(businessID)
                            .prodPage(currentPage)
                            .prodPageSize(99)
                            .build(),
                    Response::getData
            )
            .mapNotNull(GetBusinessProductsQuery.Data::business)
            .mapNotNull(business -> business.products)
            .mapNotNull(products -> {
                final GetBusinessProductsQuery.PageInfo pageInfo = products.pageInfo();
                maxPages.set(Optional.ofNullable(pageInfo.totalPages()).orElse(0));
                return products.edges();
            })
            .blockOptional().orElse(Collections.emptyList());
            // Take the edges found as a stream
            return edges.stream()
                .map(GetBusinessProductsQuery.Edge::node)
                .map(node -> node.fragments().productFragment())
                .collect(Collectors.toList());
        });
    }
    /* End Products */

    /* Start Accounts */
    @Cacheable(cacheNames = {WAVE_ACCOUNTS})
    public Mono<DataPage<AccountFragment>> getAccountsPage(
            final Token token,
            final String businessID,
            final Integer page,
            final Integer pageSize,
            final List<AccountTypeValue> types,
            final List<AccountSubtypeValue> subtypes
    ) {
        final List<AccountTypeValue> resolvedTypes = Optional.ofNullable(types).orElse(Collections.emptyList());
        final List<AccountSubtypeValue> resolvedSubTypes = Optional.ofNullable(subtypes).orElse(Collections.emptyList());
        return processQuery(
                provideGraphQLClient(token),
                GetBusinessAccountsQuery.builder()
                    .businessId(businessID)
                    .accPage(page)
                    .accPageSize(pageSize)
                    .types(resolvedTypes)
                    .subTypes(resolvedSubTypes)
                    .build(),
                (dataResponse) -> {
                    // Throw the errors if any
                    GraphQLUtil.throwErrors(dataResponse);
                    final GetBusinessAccountsQuery.Data data = dataResponse.getData();
                    final Optional<GetBusinessAccountsQuery.Accounts> customersOpt = Optional
                            .ofNullable(data)
                            .map(GetBusinessAccountsQuery.Data::business)
                            .map(GetBusinessAccountsQuery.Business::accounts);
                    // Otherwise, process the response
                    final List<AccountFragment> businessFragments = customersOpt
                            .map(GetBusinessAccountsQuery.Accounts::edges)
                            .map(edges -> unwrapNestedElement(edges, GetBusinessAccountsQuery.Edge::node))
                            .map(nodes -> unwrapNestedElement(nodes, GetBusinessAccountsQuery.Node::fragments))
                            .map(fragments -> unwrapNestedElement(fragments, GetBusinessAccountsQuery.Node.Fragments::accountFragment))
                            .orElse(Collections.emptyList());
                    final Pagination pagination = customersOpt
                            .map(GetBusinessAccountsQuery.Accounts::pageInfo)
                            .map(pageInfo -> new Pagination(pageSize, pageInfo.currentPage(), pageInfo.totalPages(), pageInfo.totalCount()))
                            .orElse(Pagination.UNKNOWN);
                    return new DataPage<>(pagination, businessFragments);
                }
        );
    }
    /* End Accounts */


    /* Start Utility Functions */
    public <I> Flux<I> getAll(final Token token,
                              final DataFetcher<I> fetcher) {
        return Flux.create((sink) -> {
            final AtomicReference<Integer> currentPage = new AtomicReference<>(1);
            final AtomicReference<Integer> maxPage = new AtomicReference<>(0);
            final ApolloClient gQLClient = getClientProviderGQL().getClient(token);
            do {
                try {
                    fetcher.fetch(gQLClient, currentPage.get(), maxPage)
                        .stream()
                        .filter(Objects::nonNull)
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
    /* End Utility functions */

    @FunctionalInterface
    interface DataFetcher<I> {
        Collection<I> fetch(final ApolloClient gQLClient,
                            final int currentPage,
                            final AtomicReference<Integer> maxPages);
    }
}
