package com.xclydes.finance.longboard.svc;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.exception.ApolloException;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.FragmentPage;
import com.xclydes.finance.longboard.models.Pagination;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.ArrayUtil;
import com.xclydes.finance.longboard.util.GraphQLUtil;
import com.xclydes.finance.longboard.wave.BusinessListQuery;
import com.xclydes.finance.longboard.wave.GetBusinessQuery;
import com.xclydes.finance.longboard.wave.GetUserQuery;
import com.xclydes.finance.longboard.wave.fragment.BusinessFragment;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.xclydes.finance.longboard.config.CacheConfig.*;
import static com.xclydes.finance.longboard.util.GraphQLUtil.processQuery;
import static com.xclydes.finance.longboard.util.GraphQLUtil.unwrapNestedElement;

@Service
@Getter
public class WaveSvc {

    public static Consumer<ApolloException> ApolloExceptionNoOp = (e) -> {
    };

    public static final String ID_BUSINESS = "Business";
    public static final String ID_PRODUCT = "Product";
    public static final String ID_ACCOUNT = "Account";
    public static final String ID_INVOICE = "Invoice";
    public static final String ID_TRANSACTION = "Transaction";

    private final IClientProvider<ApolloClient> clientProviderGQL;
    private final IClientProvider<WebClient> clientProviderRest;
    private final String loginUrl;
    private final String clientId;
    private final List<String> scopes;
    private final String callback;

    public WaveSvc(@Qualifier("wave-graphql") final IClientProvider<ApolloClient> clientProviderGQL,
                   @Qualifier("wave-rest") final IClientProvider<WebClient> clientProviderRest,
                   @Value("${longboard.wave.oauth.login}") final String LoginUrl,
                   @Value("${longboard.wave.client.key}") final String clientId,
                   @Value("${longboard.wave.oauth.scopes}") final List<String> scopes,
                   @Value("${longboard.wave.oauth.callback}") final String callback) {
        this.clientProviderGQL = clientProviderGQL;
        this.clientProviderRest = clientProviderRest;
        this.loginUrl = LoginUrl;
        this.clientId = clientId;
        this.scopes = scopes;
        this.callback = callback;
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

    public Mono<RequestToken> getLoginUrl(final String state) {
        final UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
                .fromHttpUrl(loginUrl)
                .queryParam("response_type", "code")
                // Add the state attribute
                .queryParam("state", state)
                // Add the client ID
                .queryParam("client_id", clientId)
                // Add the scopes
                .queryParam("scope", String.join(" ", scopes));
        // If a callback is set
        if (StringUtils.hasText(callback)) {
            // Add the callback URL
            uriComponentsBuilder.queryParam("redirect_uri", callback);
        }
        // return the built URL
        return Mono.just(new RequestToken(state, null, uriComponentsBuilder.toUriString()));
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
    public Mono<FragmentPage<BusinessFragment>> businesses(final Token token, final Integer page, final Integer pageSize) {
        return processQuery(
                provideGraphQLClient(token),
                new BusinessListQuery(Input.fromNullable(page), Input.fromNullable(pageSize)),
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
                    return new FragmentPage<>(pagination, businessFragments);
                }
        );
    }

    @Cacheable(cacheNames = {WAVE_BUSINESS})
    public Mono<Optional<GetBusinessQuery.Business>> business(final Token token, final String businessID) {
        return processQuery(provideGraphQLClient(token), new GetBusinessQuery(businessID), (dataResponse) -> {
            // Throw the errors if any
            GraphQLUtil.throwErrors(dataResponse);
            // Otherwise, process the response
            return Optional.ofNullable(dataResponse.getData())
                    .map(GetBusinessQuery.Data::business);
        });
    }
}
