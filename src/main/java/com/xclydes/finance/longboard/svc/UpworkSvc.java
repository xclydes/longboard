package com.xclydes.finance.longboard.svc;

import com.Upwork.api.OAuthClient;
import com.Upwork.api.Routers.Organization.Companies;
import com.Upwork.api.Routers.Organization.Teams;
import com.Upwork.api.Routers.Organization.Users;
import com.Upwork.api.Routers.Reports.Finance.Accounts;
import com.Upwork.api.Routers.Reports.Finance.Earnings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
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
    private final String callbackUrl;
    private final String earningsParams;
    private final String accountingParams;

    public UpworkSvc(@Qualifier("upwork") IClientProvider<OAuthClient> clientProvider,
                     @Value("${longboard.upwork.client.callback}") final String callbackUrl,
                     @Value("${longboard.upwork.params.earnings.fields}") String earningsParams,
                     @Value("${longboard.upwork.params.account.fields}") String accountingParams) {
        this.clientProvider = clientProvider;
        this.callbackUrl = callbackUrl;
        this.earningsParams = earningsParams;
        this.accountingParams = accountingParams;
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
    public Optional<ObjectNode> user(final Token token) {
        try {
            // Initialize the user function
            final Users upworkUsersFn = new Users(getClientProvider().getClient(token));
            return Optional.ofNullable(JsonUtil.toJacksonObject(upworkUsersFn.getMyInfo().getJSONObject("user")));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Cacheable(cacheNames = {UPWORK_COMPANY})
    public Optional<ArrayNode> companies(final Token token) {
        try {
            final Companies companies = new Companies(getClientProvider().getClient(token));
            return Optional.ofNullable(JsonUtil.toJacksonArray(companies.getList().getJSONArray("companies")));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Cacheable(cacheNames = {UPWORK_TEAMS})
    public Optional<ArrayNode> teams(final Token token) {
        try {
            final Teams teams = new Teams(getClientProvider().getClient(token));
            return Optional.ofNullable(JsonUtil.toJacksonArray(teams.getList().getJSONArray("teams")));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    public ArrayNode earnings(final Token token,
                              final LocalDate from,
                              final LocalDate to,
                              final String userReference
    ) {
        return this.earnings(token, from, to, userReference, this.earningsParams);
    }

    @Cacheable(cacheNames = {UPWORK_EARNINGS})
    public ArrayNode earnings(final Token token,
                              final LocalDate from,
                              final LocalDate to,
                              final String userReference,
                              final String fields
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, userReference);
        try {
            // Build the parameters
            final HashMap<String, String> params = new HashMap<>(1);
            // Build the select query
            params.put("tq", String.format("SELECT %s WHERE date >= '%s' AND date <= '%s'", fields, dateFormatSQL.format(from), dateFormatSQL.format(to)));
            // Re-map the entries
            final Earnings earnings = new Earnings(getClientProvider().getClient(token));
            final JSONObject earningResponse = earnings.getByFreelancer(resolvedUserReference, params);
            // Flatten the table
            return remapTable(earningResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return JsonUtil.newArray();
        }
    }

    public ArrayNode accountsForEntity(final Token token, final LocalDate from, final LocalDate to,
                                       final String acctRef
    ) {
        return this.accountsForEntity(token, from, to, acctRef, accountingParams);
    }

    @Cacheable(cacheNames = {UPWORK_ACCOUNTING})
    public ArrayNode accountsForEntity(final Token token, final LocalDate from, final LocalDate to,
                                       final String acctRef,
                                       final String fields
    ) {
        // If a valid reference is not found
        if (!StringUtils.hasText(acctRef)) throw new IllegalStateException("A valid account reference is required");
        try {
            // Build the parameters
            final HashMap<String, String> params = new HashMap<>(1);
            // Build the select query
            params.put("tq", String.format("SELECT %s WHERE date >= '%s' AND date <= '%s'", fields, dateFormatSQL.format(from), dateFormatSQL.format(to)));
            // Re-map the entries
            final Accounts accounts = new Accounts(getClientProvider().getClient(token));
            final JSONObject accountResponse = accounts.getSpecific(acctRef, params);
            // Flatten the table
            return remapTable(accountResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return JsonUtil.newArray();
        }
    }

    public ArrayNode accountsForUser(final Token token, LocalDate from, LocalDate to,
                                     final String userReference) {
        return this.accountsForUser(token, from, to, userReference, earningsParams);
    }

    @Cacheable(cacheNames = {UPWORK_ACCOUNTS})
    public ArrayNode accountsForUser(final Token token,
                                     final LocalDate from,
                                     final LocalDate to,
                                     final String userReference,
                                     final String fields) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, userReference);
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        params.put("tq", String.format("SELECT %s WHERE date >= '%s' AND date <= '%s'", fields, dateFormatSQL.format(from), dateFormatSQL.format(to)));
        try {
            // Re-map the entries
            final Accounts accounts = new Accounts(getClientProvider().getClient(token));
            final JSONObject accountResponse = accounts.getOwned(userReference, params);
            // Flatten the table
            return remapTable(accountResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return JsonUtil.newArray();
        }
    }

    private String resolveUserReference(final Token token, final String inputRef) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = Optional.ofNullable(inputRef)
                .filter(StringUtils::hasText)
                .orElseGet(() -> user(token).map(user -> user.required("reference").textValue()).orElse(null));
        // If a valid reference is not found
        if (!StringUtils.hasText(resolvedUserReference))
            throw new IllegalStateException("Unable to determine user reference");
        return resolvedUserReference;
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
                JsonUtil.jsonArrayToList(table.getJSONArray("rows")).stream()
                        .map(row -> {
                            try {
                                return row.getJSONArray("c");
                            } catch (JSONException e) {
                                return new JSONArray();
                            }
                        })
                        .map(valueArr -> JsonUtil.jsonArrayToList(valueArr).stream().map(vJson -> vJson.optString("v", "")).collect(Collectors.toList()))
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
