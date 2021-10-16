package com.xclydes.finance.longboard.svc;

import com.Upwork.api.OAuthClient;
import com.Upwork.api.Routers.Organization.Companies;
import com.Upwork.api.Routers.Organization.Teams;
import com.Upwork.api.Routers.Organization.Users;
import com.Upwork.api.Routers.Reports.Finance.Accounts;
import com.Upwork.api.Routers.Reports.Finance.Earnings;
import com.Upwork.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.DatesUtil;
import com.xclydes.finance.longboard.util.JsonUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import oauth.signpost.OAuthConsumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xclydes.finance.longboard.config.CacheConfig.*;

@Service
@Getter
@Slf4j
public class UpworkSvc {

    public static final ZoneOffset TimeZone = ZoneOffset.UTC;
    public static final DateTimeFormatter dateFormatSQL = DateTimeFormatter.ISO_DATE;
    public static final DateTimeFormatter dateFormatReport = DatesUtil.formatterReport();
    public static final DateTimeFormatter dateFormatDescription = DatesUtil.formatterDescriptive();
    public static final Pattern patternInvoiceDescription = Pattern.compile("^\\((.+)\\) ([^-\\s]+) - (\\d{1,2}):(\\d{2})\\shrs @ \\$([^/]*)/hr - ([\\d-/]*) - ([\\d-/]*)$");

    private final IClientProvider<OAuthClient> clientProvider;
    private final ObjectMapper objectMapper;
    private final String callbackUrl;

    public UpworkSvc(@Qualifier("upwork") IClientProvider<OAuthClient> clientProvider,
                     final ObjectMapper objectMapper,
                     @Value("${longboard.upwork.client.callback}") final String callbackUrl) {
        this.clientProvider = clientProvider;
        this.objectMapper = objectMapper;
        this.callbackUrl = callbackUrl;
    }

    private ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private String getCallbackUrl() {
        return callbackUrl;
    }

    public RequestToken startLogin() {
        // Get the url
        final OAuthClient client = getClientProvider().getClient();
        final OAuthConsumer oAuthConsumer = client.getOAuthConsumer();
        final String url = client.getAuthorizationUrl( this.getCallbackUrl() );
        return new RequestToken(oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret(), url);
    }

    /**
     * Exchanges the request token and verifier supplied for an access token
     * @param token The request token to submit
     * @param verifier The verifier to submit
     * @return The access token returned
     */
    @Cacheable(cacheNames = {UPWORK_ACCESSTOKEN})
    public Token getAccessToken(final Token token, final String verifier) {
        // Get the url
        final OAuthClient client = getClientProvider().getClient();
        // Set the request keys on the consumer
        client.getOAuthConsumer().setTokenWithSecret(token.getKey(), token.getSecret());
        // Perform the exchange
        return client.getAccessTokenSet(verifier);
    }

    @Cacheable(cacheNames = {UPWORK_USER})
    public Optional<User> user(final Token token) {
        try {
            // Initialize the user function
            final Users upworkUsersFn = new Users(getClientProvider().getClient(token));
            final JSONObject myInfo = upworkUsersFn.getMyInfo();
            final JSONObject userJson = myInfo.getJSONObject("user");
            final User user = fromJson(userJson, User.class);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Cacheable(cacheNames = {UPWORK_COMPANY})
    public List<Company> companies(final Token token) {
        try {
            final Companies companies = new Companies(getClientProvider().getClient(token));
            final JSONObject companiesList = companies.getList();
            final JSONArray companiesJson = companiesList.getJSONArray("companies");
            return fromJsonArray(companiesJson, Company.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Cacheable(cacheNames = {UPWORK_TEAMS})
    public List<Team> teams(final Token token) {
        try {
            final Teams teams = new Teams(getClientProvider().getClient(token));
            final JSONObject teamsList = teams.getList();
            final JSONArray teamsJson = teamsList.getJSONArray("teams");
            return fromJsonArray(teamsJson, Team.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Cacheable(cacheNames = {UPWORK_EARNINGS})
    public  List<Earning> earnings(final Token token,
                              final LocalDate from,
                              final LocalDate to,
                              final String userReference
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, userReference);
        try {
            // Build the parameters
            final HashMap<String, String> params = new HashMap<>(1);
            // Build the select query
            params.put("tq", String.format("SELECT reference,buyer_team__reference,date,amount,type,subtype,description,date_due WHERE date >= '%s' AND date <= '%s'", dateFormatSQL.format(from), dateFormatSQL.format(to)));
            // Re-map the entries
            final Earnings earnings = new Earnings(getClientProvider().getClient(token));
            final JSONObject earningResponse = earnings.getByFreelancer(resolvedUserReference, params);
            // Flatten the table
            return fromJsonArray(remapTable(earningResponse), Earning.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Cacheable(cacheNames = {UPWORK_ACCOUNTING})
    public List<Accounting> accountsForEntity(final Token token,
                                              final LocalDate from,
                                              final LocalDate to,
                                              final String acctRef
    ) {
        // If a valid reference is not found
        if (!StringUtils.hasText(acctRef)) throw new IllegalStateException("A valid account reference is required");
        try {
            return performAccountingQuery(
                    token,
                    from,
                    to,
                    (accounts, params) -> {
                        try {
                            return accounts.getSpecific(acctRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Cacheable(cacheNames = {UPWORK_ACCOUNTS})
    public List<Accounting> accountsForUser(final Token token,
                                     final LocalDate from,
                                     final LocalDate to,
                                     final String userReference) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, userReference);
        try {
            return performAccountingQuery(
                    token,
                    from,
                    to,
                    (accounts, params) -> {
                        try {
                           return accounts.getOwned(resolvedUserReference, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Accounting> performAccountingQuery(final Token token,
                                                    final LocalDate from,
                                                    final LocalDate to,
                                                    final BiFunction<Accounts, HashMap<String, String>, JSONObject> exec) throws Exception {
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        params.put("tq", String.format(
                "SELECT accounting_entity__reference,reference,buyer_team__reference,date,amount,type,subtype,description,date_due WHERE date >= '%s' AND date <= '%s'"
                , dateFormatSQL.format(from), dateFormatSQL.format(to))
        );
        // Re-map the entries
        final Accounts accounts = new Accounts(getClientProvider().getClient(token));
        final JSONObject accountResponse = exec.apply(accounts, params);
        return fromJsonArray(remapTable(accountResponse), Accounting.class);
    }

    private String resolveUserReference(final Token token, final String inputRef) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = Optional.ofNullable(inputRef)
                .filter(StringUtils::hasText)
                .orElseGet(() -> user(token).map(user -> user.reference).orElse(null));
        // If a valid reference is not found
        if (!StringUtils.hasText(resolvedUserReference))
            throw new IllegalStateException("Unable to determine user reference");
        return resolvedUserReference;
    }

    private <T> T fromJson(final JSONObject json, Class<T> cls) throws JsonProcessingException {
        return getObjectMapper().readValue(json.toString(), cls);
    }

    private <T> List<T> fromJsonArray(final JSONArray json, Class<T> cls) throws JsonProcessingException {
        return getObjectMapper().readValue(json.toString(),
                getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
    }

    private <T> List<T> fromJsonArray(final ArrayNode json, Class<T> cls) throws JsonProcessingException {
        return getObjectMapper().readValue(json.toString(),
                getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
    }

    private ArrayNode remapTable(final JSONObject response) {
        final ArrayNode earnings = JsonUtil.newArray();
        try {
            // Extract the table
            if (response.has("table")) {
                final JSONObject table = response.getJSONObject("table");
                // Get the heading
                final List<String> headings = JsonUtil.jsonArrayToList(table.getJSONArray("cols"))
                        .stream().map(o -> {
                            try {
                                return o.getString("label");
                            } catch (JSONException e) {
                                return "";
                            }
                        })
                        .collect(Collectors.toList());
                // Process each row
                JsonUtil.jsonArrayToList(table.getJSONArray("rows"))
                        .parallelStream()
                        .map(row -> {
                            try {
                                return row.getJSONArray("c");
                            } catch (JSONException e) {
                                return new JSONArray();
                            }
                        })
                        .map(valueArr -> JsonUtil.jsonArrayToList(valueArr).parallelStream().map(vJson -> vJson.optString("v", "")).collect(Collectors.toList()))
                        .forEach(valueList -> {
                                    final ObjectNode jsonObj = JsonUtil.newObject();
                                    for (int index = 0; index < valueList.size(); index++) {
                                        final String value = valueList.get(index);
                                        final String heading = headings.get(index);
                                        jsonObj.put(heading, value);
                                        earnings.add(jsonObj);
                                    }
                                }
                        );
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earnings;
    }

}
