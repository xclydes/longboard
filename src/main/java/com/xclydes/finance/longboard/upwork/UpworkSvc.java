package com.xclydes.finance.longboard.upwork;

import com.Upwork.api.OAuthClient;
import com.Upwork.api.Routers.Organization.Companies;
import com.Upwork.api.Routers.Organization.Teams;
import com.Upwork.api.Routers.Organization.Users;
import com.Upwork.api.Routers.Reports.Finance.Accounts;
import com.Upwork.api.Routers.Reports.Finance.Earnings;
import com.xclydes.finance.longboard.upwork.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.DatesUtil;
import com.xclydes.finance.longboard.util.JsonUtil;
import com.xclydes.finance.longboard.util.ValidationUtil;
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
        // if the url is invalid
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

    /**
     *
     * @param token
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_USER})
    public Optional<User> user(final Token token, final String ref) {
        // Initialize the user function
        final Users upworkUsersFn = new Users(getClientProvider().getClient(token));
        final User user = Optional.ofNullable(ref)
                .filter(StringUtils::hasText)
                .map(userRef -> {
                    User found = null;
                    try {
                        final JSONObject myInfo = upworkUsersFn.getSpecific(userRef);
                        final JSONObject userJson = myInfo.getJSONObject("user");
                        found = fromJson(userJson, User.class);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    return found;
                })
                .orElseGet(() -> {
                    User found = null;
                    try {
                        final JSONObject myInfo = upworkUsersFn.getMyInfo();
                        final JSONObject userJson = myInfo.getJSONObject("user");
                        found = fromJson(userJson, User.class);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    return found;
                });
        return Optional.ofNullable(user);
    }

    /**
     *
     * @param token
     * @param ref
     * @return
     */
    //@Cacheable(cacheNames = {UPWORK_COMPANY})
    public List<Company> companies(final Token token, final String ref) {
        // Initialize the Upwork wrapper
        final Companies companies = new Companies(getClientProvider().getClient(token));
        // Process the inputs
        return Optional.ofNullable(ref)
            .filter(StringUtils::hasText)
            .map(companyRef -> {
                List<Company> companyLst = Collections.emptyList();
                try {
                    final JSONObject companiesList = companies.getSpecific(ref);
                    final JSONObject companiesJson = companiesList.getJSONObject("company");
                    companyLst = Collections.singletonList(fromJson(companiesJson, Company.class));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                return companyLst;
            })
            .orElseGet(() -> {
                List<Company> companyLst = Collections.emptyList();
                try {
                    final JSONObject companiesList = companies.getList();
                    final JSONArray companiesJson = companiesList.getJSONArray("companies");
                    companyLst = fromJsonArray(companiesJson, Company.class);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                return companyLst;
            });
    }

    /**
     *
     * @param token
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_TEAMS})
    public List<Team> teams(final Token token, final String ref) {
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

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_USER})
    public List<Earning> earningsForUser(final Token token,
                                          final LocalDate from,
                                          final LocalDate to,
                                          final String ref
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedRef = resolveUserReference(token, ref);
        List<Earning> earningList = Collections.emptyList();
        try {
            earningList = performEarningsQuery(
                token, from, to,
                (earnings, params) -> {
                    try {
                        return earnings.getByFreelancer(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earningList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_FREELANCER_COMPANY})
    public List<Earning> earningsForFreelancerCompany(final Token token,
                                          final LocalDate from,
                                          final LocalDate to,
                                          final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid freelancer/company reference is required");
        List<Earning> earningList = Collections.emptyList();
        try {
            earningList = performEarningsQuery(
                token, from, to,
                (earnings, params) -> {
                    try {
                        return earnings.getByFreelancersCompany(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earningList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_FREELANCER_TEAM})
    public List<Earning> earningsForFreelancerTeam(final Token token,
                                          final LocalDate from,
                                          final LocalDate to,
                                          final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid freelancer/team reference is required");
        List<Earning> earningList = Collections.emptyList();
        try {
            earningList = performEarningsQuery(
                token, from, to,
                (earnings, params) -> {
                    try {
                        return earnings.getByFreelancersTeam(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earningList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_TEAM})
    public List<Earning> earningsForBuyersTeam(final Token token,
                                          final LocalDate from,
                                          final LocalDate to,
                                          final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid buyers/team reference is required");
        List<Earning> earningList = Collections.emptyList();
        try {
            earningList = performEarningsQuery(
                token, from, to,
                (earnings, params) -> {
                    try {
                        return earnings.getByBuyersTeam(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earningList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public  List<Earning> earningsForBuyersCompany(final Token token,
                                          final LocalDate from,
                                          final LocalDate to,
                                          final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid buyers/company reference is required");
        List<Earning> earningList = Collections.emptyList();
        try {
            earningList = performEarningsQuery(
                token, from, to,
                (earnings, params) -> {
                    try {
                        return earnings.getByBuyersCompany(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earningList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param ref
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_ACCOUNTING_ENTITY})
    public List<Accounting> accountsForEntity(final Token token,
                                              final LocalDate from,
                                              final LocalDate to,
                                              final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid account reference is required");
        List<Accounting> accountingList = Collections.emptyList();
        try {
            accountingList = performAccountingQuery(
                    token,
                    from,
                    to,
                    (accounts, params) -> {
                        try {
                            return accounts.getSpecific(resolvedRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return accountingList;
    }

    /**
     *
     * @param token
     * @param from
     * @param to
     * @param userReference
     * @return
     */
    @Cacheable(cacheNames = {UPWORK_ACCOUNTING_USER})
    public List<Accounting> accountsForUser(final Token token,
                                     final LocalDate from,
                                     final LocalDate to,
                                     final String userReference) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, userReference);
        List<Accounting> accountingList = Collections.emptyList();
        try {
            accountingList = performAccountingQuery(
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
        }
        return accountingList;
    }

    /**
     * Performs a query to getch accounting related activites from Upwork.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_financial-reports-fields">Financial Reports</a>
     * @param token The user token to be used for authentication
     * @param from The date from which to start the query
     * @param to The date up to which the query should check
     * @param exec The callback to perform the query execution and extract the appropriate result.
     *             By result that means a typical report representation which can be mapped to a list of Accounting
     *             records.
     * @return The list of records found, if any.
     * @throws Exception If any errors were encountered while processing the request.
     */
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

    /**
     * Performs a query to fetch time related activities from Upwork.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_time-reports-fields">Time Reports</a>
     * @param token The user token to be used for authentication
     * @param from The date from which to start the query
     * @param to The date up to which the query should check
     * @param exec The callback to perform the query execution and extract the appropriate result.
     *             By result that means a typical report representation which can be mapped to a list of Earning
     *             records.
     * @return The list of records found, if any.
     * @throws Exception If any errors were encountered while processing the request.
     */
    private List<Earning> performEarningsQuery(final Token token,
                                                    final LocalDate from,
                                                    final LocalDate to,
                                                    final BiFunction<Earnings, HashMap<String, String>, JSONObject> exec) throws Exception {
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        // Build the select query
        params.put("tq", String.format("SELECT reference,buyer_team__reference,date,amount,type,subtype,description,date_due WHERE date >= '%s' AND date <= '%s'", dateFormatSQL.format(from), dateFormatSQL.format(to)));
        // Re-map the entries
        final Earnings earnings = new Earnings(getClientProvider().getClient(token));
        final JSONObject earningResponse = exec.apply(earnings, params);
        // Flatten the table
        return fromJsonArray(remapTable(earningResponse), Earning.class);
    }

    /**
     *
     * @param token
     * @param inputRef
     * @return
     */
    private String resolveUserReference(final Token token, final String inputRef) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = Optional.ofNullable(inputRef)
                .filter(StringUtils::hasText)
                .orElseGet(() -> user(token, inputRef).map(user -> user.reference).orElse(null));
        // If a valid reference is not found
        if (!StringUtils.hasText(resolvedUserReference))
            throw new IllegalStateException("Unable to determine user reference");
        return resolvedUserReference;
    }

    /**
     *
     * @param json
     * @param cls
     * @param <T>
     * @return
     */
    private <T> T fromJson(final JSONObject json, Class<T> cls) {
        try {
            return getObjectMapper().readValue(json.toString(), cls);
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
    }

    /**
     *
     * @param json
     * @param cls
     * @param <T>
     * @return
     */
    private <T> List<T> fromJsonArray(final JSONArray json, Class<T> cls){
        try {
            return getObjectMapper().readValue(json.toString(),
                    getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
    }

    private <T> List<T> fromJsonArray(final ArrayNode json, Class<T> cls) {
        try {
            return getObjectMapper().readValue(json.toString(),
                    getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
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
                                    }
                                    earnings.add(jsonObj);
                                }
                        );
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earnings;
    }
}
