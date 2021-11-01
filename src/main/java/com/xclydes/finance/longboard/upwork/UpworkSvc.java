package com.xclydes.finance.longboard.upwork;

import com.Upwork.api.OAuthClient;
import com.Upwork.api.Routers.Organization.Companies;
import com.Upwork.api.Routers.Organization.Teams;
import com.Upwork.api.Routers.Organization.Users;
import com.Upwork.api.Routers.Reports.Finance.Accounts;
import com.Upwork.api.Routers.Reports.Finance.Billings;
import com.Upwork.api.Routers.Reports.Finance.Earnings;
import com.Upwork.api.Routers.Reports.Time;
import com.Upwork.api.Routers.Workdiary;
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
import java.util.function.Function;
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
        final String url = client.getAuthorizationUrl(this.getCallbackUrl());
        // if the url is invalid
        return new RequestToken(oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret(), url);
    }

    /**
     * Exchanges the request token and verifier supplied for an access token
     *
     * @param token    The request token to submit
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
     * Gets the details of the current user or one with the ref given if any
     *
     * @param token The access token to be used
     * @return The user requested, if found
     */
    @Cacheable(cacheNames = {UPWORK_USER})
    public Optional<User> user(final Token token, final String ref) {
        // Initialize the user function
        final Users upworkUsersFn = new Users(getClientProvider().getClient(token));
        final User user = Optional.ofNullable(ref)
                .filter(StringUtils::hasText)
                .map(userRef -> {
                    try {
                        final JSONObject userInfoResponse = upworkUsersFn.getSpecific(userRef);
                        // Check for errors
                        checkForException(userInfoResponse);
                        final JSONObject userJson = userInfoResponse.getJSONObject("user");
                        return fromJson(userJson, User.class);
                    } catch (JSONException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                })
                .orElseGet(() -> {
                    try {
                        final JSONObject myInfoResponse = upworkUsersFn.getMyInfo();
                        // Check for errors
                        checkForException(myInfoResponse);
                        final JSONObject userJson = myInfoResponse.getJSONObject("user");
                        return fromJson(userJson, User.class);
                    } catch (JSONException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
        return Optional.ofNullable(user);
    }

    /**
     * Gets the list of or a specific team
     *
     * @param token The access token to be used
     * @param ref   The team reference to be used, if any.
     * @return The list of users on the specific team
     */
    @Cacheable(cacheNames = {UPWORK_USER_BY_TEAM})
    public List<User> usersInTeam(final Token token, final String ref) {
        try {
            final Teams teams = new Teams(getClientProvider().getClient(token));
            final JSONObject teamsListResponse = teams.getUsersInTeam(ref);
            // Check for errors
            checkForException(teamsListResponse);
            final JSONArray teamsJson = teamsListResponse.getJSONArray("users");
            return fromJsonArray(teamsJson, User.class);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the list of companies or a specific company
     *
     * @param token The access token to be used
     * @param ref   The company reference to filter for
     * @return The list of companies or specific company
     */
    @Cacheable(cacheNames = {UPWORK_COMPANY})
    public List<Company> companies(final Token token, final String ref) {
        // Initialize the Upwork wrapper
        final Companies companies = new Companies(getClientProvider().getClient(token));
        // Process the inputs
        return Optional.ofNullable(ref)
                .filter(StringUtils::hasText)
                .map(companyRef -> {
                    try {
                        final JSONObject companiesListResponse = companies.getSpecific(ref);
                        // Check for errors
                        checkForException(companiesListResponse);
                        final JSONObject companiesJson = companiesListResponse.getJSONObject("company");
                        return Collections.singletonList(fromJson(companiesJson, Company.class));
                    } catch (JSONException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                })
                .orElseGet(() -> {
                    try {
                        final JSONObject companiesListResponse = companies.getList();
                        // Check for errors
                        checkForException(companiesListResponse);
                        final JSONArray companiesJson = companiesListResponse.getJSONArray("companies");
                        return fromJsonArray(companiesJson, Company.class);
                    } catch (JSONException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Gets the list of or a specific team
     *
     * @param token The access token to be used
     * @return The list of all teams or the specific team
     */
    @Cacheable(cacheNames = {UPWORK_TEAMS})
    public List<Team> teams(final Token token) {
        try {
            final Teams teams = new Teams(getClientProvider().getClient(token));
            final JSONObject teamsListResponse = teams.getList();
            // Check for errors
            checkForException(teamsListResponse);
            final JSONArray teamsJson = teamsListResponse.getJSONArray("teams");
            return fromJsonArray(teamsJson, Team.class);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

//    @Cacheable(cacheNames = {UPWORK_TEAMS})
    public List<Team> companyTeams(final Token token, final String companyRef) {
        // If a valid reference is not found
        final String resolvedEntityRef = ValidationUtil.requires(companyRef,
                StringUtils::hasText, "A valid company reference is required");
        try {
            final Companies companiesRoute = new Companies(getClientProvider().getClient(token));
            final JSONObject teamsListResponse = companiesRoute.getTeams(resolvedEntityRef);
            // Check for errors
            checkForException(teamsListResponse);
            final JSONArray teamsJson = teamsListResponse.getJSONArray("teams");
            return fromJsonArray(teamsJson, Team.class);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public List<DiaryRecord> workDiaryForCompany(final Token token,
                                                 final LocalDate date,
                                                 final String teamOrCompanyID) {
        // If a valid reference is not found
        final String resolvedEntityID = ValidationUtil.requires(teamOrCompanyID,
                StringUtils::hasText, "A valid freelancer/company ID is required");
        try {
            final Workdiary workDiaryRoute = new Workdiary(getClientProvider().getClient(token));
            final HashMap<String, String> params = new HashMap<>();
            //log.debug(String.format("Fetching work diary for %s on %s", resolvedEntityID, date));
            final JSONObject workDiaryResponse = workDiaryRoute.get(resolvedEntityID,
                    dateFormatReport.format(date), params);
            // Check for errors
            checkForException(workDiaryResponse);
            final JSONObject snapshotDataJson = workDiaryResponse.getJSONObject("data");
            final JSONArray snapshotsJson = snapshotDataJson.getJSONArray("snapshots");
            return fromJsonArray(snapshotsJson, DiaryRecord.class);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets Earning records for a specific freelancer/user
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference for the freelancer
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_USER})
    public List<TimeRecord> earningsForUser(final Token token,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final String ref
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedRef = resolveUserReference(token, ref);
        return performTimeQuery(
                from, to, null,
                (params) -> {
                    try {
                        return getEarningsRoute(token).getByFreelancer(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Gets Billing records for a specific freelancer/user
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference for the freelancer
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_USER})
    public List<FinanceRecord> billiingsForUser(final Token token,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final String ref
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedRef = resolveUserReference(token, ref);
        return performFinancialQuery(
                from, to,
                // Disallowed fields: `comment`, `po_number`. Supported filters: `date`, `week`, `month`, `year`, `date_due`, `buyer_company__reference`, `buyer_company__id`, `buyer_team__reference`, `buyer_team__id`, `provider_company__reference`, `provider_company__id`, `assignment__reference`, `type`, `subtype`. Permissions: Exclusive Agency Member
                null,
                (params) -> {
                    try {
                        return getBillingsRoute(token).getByFreelancer(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Gets Earning records for a specific freelancer company
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the team
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_FREELANCER_COMPANY})
    public List<TimeRecord> earningsForFreelancerCompany(final Token token,
                                                         final LocalDate from,
                                                         final LocalDate to,
                                                         final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid freelancer/company reference is required");
        return performTimeQuery(
                from, to, null,
                (params) -> {
                    try {
                        return getBillingsRoute(token).getByFreelancersCompany(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Gets Earning records for a specific freelancer team
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the team
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_FREELANCER_TEAM})
    public List<TimeRecord> earningsForFreelancerTeam(final Token token,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid freelancer/team reference is required");
        return performTimeQuery(
                from, to, null,
                (params) -> {
                    try {
                        return getEarningsRoute(token).getByFreelancersTeam(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Gets Earning records for a specific client team
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the team
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_TEAM})
    public List<TimeRecord> earningsForBuyersTeam(final Token token,
                                                  final LocalDate from,
                                                  final LocalDate to,
                                                  final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid buyers/team reference is required");
        return performTimeQuery(
                    from, to, null,
                    (params) -> {
                        try {
                            return getEarningsRoute(token).getByBuyersTeam(resolvedRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Gets earning records related to a specific client company
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the company to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public List<TimeRecord> earningsForBuyersCompany(final Token token,
                                                     final LocalDate from,
                                                     final LocalDate to,
                                                     final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid buyers/company reference is required");
        return performTimeQuery(
                    from, to, null,
                    (params) -> {
                        try {
                            return getEarningsRoute(token).getByBuyersCompany(resolvedRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Gets earning records related to a specific client company
     *Fields that cannot appear in the query: `team_id`, `team_name`.
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param companyRef   The reference of the company to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public List<TimeRecord> timeByCompany(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyRef
    ) {
        // If a valid reference is not found
        final String resolvedCompanyRef = ValidationUtil.requires(companyRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        return performTimeQuery(
                    from, to,
                    // Fields that cannot appear in the query: `company_id`
        null,
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByCompany(resolvedCompanyRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Gets earning records related to a specific client company
     *Fields that cannot appear in the query: `team_id`, `team_name`.
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param companyRef   The reference of the company to be queried
     * @param agencyRef   The reference of the agency to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public List<TimeRecord> timeByAgency(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyRef,
                                         final String agencyRef
    ) {
        // If a valid reference is not found
        final String resolvedCompanyRef = ValidationUtil.requires(companyRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        // If a valid reference is not found
        final String resolvedAgencyRef = ValidationUtil.requires(agencyRef,
                StringUtils::hasText, "A valid agency reference is required");
        return performTimeQuery(
                    from, to,
                    // Fields that cannot appear in the query: `agency_id`, `agency_name`,
                    // `charges`, `charges_online`, `charges_offline`.
        null,
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByAgency(resolvedCompanyRef, agencyRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Gets earning records related to a specific client company
     *Fields that cannot appear in the query: `team_id`, `team_name`.
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param companyRef   The reference of the company to be queried
     * @param teamRef   The reference of the team to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public List<TimeRecord> timeByTeam(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyRef,
                                         final String teamRef
    ) {
        // If a valid reference is not found
        final String resolvedCompanyRef = ValidationUtil.requires(companyRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        final String resolvedTeamRef = ValidationUtil.requires(teamRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        return performTimeQuery(
                    from, to,
                    // Fields that cannot appear in the query: `team_id`, `team_name`
        null,
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByTeamFull(resolvedCompanyRef, resolvedTeamRef, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Gets accounting records for a specific entity
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the entity to be queried
     * @return The list of account records found
     */
    @Cacheable(cacheNames = {UPWORK_ACCOUNTING_ENTITY})
    public List<FinanceRecord> accountsForEntity(final Token token,
                                              final LocalDate from,
                                              final LocalDate to,
                                              final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid account reference is required");
        return performFinancialQuery(
                from,
                to,
                null,
                (params) -> {
                    try {
                        return getAccountsRoute(token).getSpecific(resolvedRef, params);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
    }

    /**
     * Gets accounting records for a specific or the current user
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param ref   The reference of the user to be queried. Assumes the current user if null.
     * @return The list of account records found
     */
    @Cacheable(cacheNames = {UPWORK_ACCOUNTING_USER})
    public List<FinanceRecord> accountsForUser(final Token token,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final String ref) {
        // Use the user provided, or the one the token belongs to
        final String resolvedUserReference = resolveUserReference(token, ref);
        return performFinancialQuery(
                    from,
                    to,
                    null,
                    (params) -> {
                        try {
                            return getAccountsRoute(token).getOwned(resolvedUserReference, params);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
    }

    /**
     * Performs a query to getch accounting related activites from Upwork.
     *
     * @param from The date from which to start the query
     * @param to   The date up to which the query should check
     * @param exec The callback to perform the query execution and extract the appropriate result.
     *             By result that means a typical report representation which can be mapped to a list of Accounting
     *             records.
     * @return The list of records found, if any.
     * @throws Exception If any errors were encountered while processing the request.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_financial-reports-fields">Financial Reports</a>
     */
    private List<FinanceRecord> performFinancialQuery(final LocalDate from,
                                                      final LocalDate to,
                                                      final String additionalFields,
                                                      final Function<HashMap<String, String>, JSONObject> exec) {
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        String queryFields = "accounting_entity__reference,reference,buyer_team__reference,date,amount,type,subtype,description,date_due";
        // If there are additional fields
        if (StringUtils.hasText(additionalFields)) {
            // Add them to the query
            queryFields += "," + additionalFields;
        }
        params.put("tq", String.format(
                "SELECT %s WHERE date >= '%s' AND date <= '%s'"
                , queryFields, dateFormatSQL.format(from), dateFormatSQL.format(to))
        );
        // Re-map the entries
        final JSONObject accountResponse = exec.apply(params);
        // Check for errors
        checkForException(accountResponse);
        return fromJsonArray(remapTable(accountResponse), FinanceRecord.class);
    }

    private Accounts getAccountsRoute(final Token token) {
        return new Accounts(getClientProvider().getClient(token));
    }

    private Billings getBillingsRoute(final Token token) {
        return new Billings(getClientProvider().getClient(token));
    }

    private Earnings getEarningsRoute(final Token token) {
        return new Earnings(getClientProvider().getClient(token));
    }

    private Time getTimeRoute(final Token token) {
        return new Time(getClientProvider().getClient(token));
    }

    /**
     * Performs a query to fetch time related activities from Upwork.
     *
     * @param from             The date from which to start the query
     * @param to               The date up to which the query should check
     * @param additionalFields Any additional fields to include in the query
     * @param exec             The callback to perform the query execution and extract the appropriate result.
     *                         By result that means a typical report representation which can be mapped to a list of Earning
     *                         records.
     * @return The list of records found, if any.
     * @throws Exception If any errors were encountered while processing the request.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_time-reports-fields">Time Reports</a>
     */
    private List<TimeRecord> performTimeQuery(final LocalDate from,
                                              final LocalDate to,
                                              final String additionalFields,
                                              final Function<HashMap<String, String>, JSONObject> exec) {
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        String queryFields = "reference,buyer_team__reference,date,amount,type,subtype,description,date_due";
        // If there are additional fields
        if (StringUtils.hasText(additionalFields)) {
            // Add them to the query
            queryFields += "," + additionalFields;
        }
        // Build the select query
        params.put("tq", String.format("SELECT %s WHERE date >= '%s' AND date <= '%s'", queryFields, dateFormatSQL.format(from), dateFormatSQL.format(to)));
        // Execute th request
        final JSONObject earningResponse = exec.apply(params);
        // Check for errors
        checkForException(earningResponse);
        // Flatten the table
        final ArrayNode remappedTable = remapTable(earningResponse);
        // Process to the required class
        return fromJsonArray(remappedTable, TimeRecord.class);
    }

    /**
     * Gets the user reference to be used, given a possible reference.
     *
     * @param token    The token to validate against
     * @param inputRef The possible user reference, or null
     * @return The reference that was given or that of the user who owns the token
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
     * Converts the JSON object to an instance given class
     *
     * @param json The JSON array to be converted
     * @param cls  The type of the object to be created
     * @param <T>  The generic type/class hint
     * @return The converted list of objects
     */
    private <T> T fromJson(final JSONObject json, Class<T> cls) {
        try {
            return getObjectMapper().readValue(json.toString(), cls);
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
    }

    /**
     * Converts the contents of a JSON array to a list of the given type
     *
     * @param json The JSON array to be converted
     * @param cls  The type of the internal elements/objects
     * @param <T>  The generic type/class hint
     * @return The converted list of objects
     */
    private <T> List<T> fromJsonArray(final JSONArray json, Class<T> cls) {
        try {
            return getObjectMapper().readValue(json.toString(),
                    getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
    }

    /**
     * Converts the contents of a JSON array to a list of the given type
     *
     * @param json The JSON array to be converted
     * @param cls  The type of the internal elements/objects
     * @param <T>  The generic type/class hint
     * @return The converted list of objects
     */
    private <T> List<T> fromJsonArray(final ArrayNode json, Class<T> cls) {
        try {
            return getObjectMapper().readValue(json.toString(),
                    getObjectMapper().getTypeFactory().constructCollectionType(List.class, cls));
        } catch (JsonProcessingException jEx) {
            throw new RuntimeException(jEx);
        }
    }

    private static ArrayNode remapTable(final JSONObject response) {
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
            } else {
                throw new IllegalArgumentException("Table attribute missing");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return earnings;
    }

    /**
     * Processes a response from Upwork to re-cast an error messages received
     * @param jsonResponse The response to be checked
     * @throws RuntimeException If any errors are discovered
     */
    private static void checkForException(final JSONObject jsonResponse) throws RuntimeException {
        JSONObject errorObject = null;
            try {
                // If the response has an error
                if (jsonResponse.has("errors")) {
                    // It should be an array
                    final JSONArray errors = jsonResponse.getJSONArray("errors");
                    // if there are entries
                    if (errors.length() > 0) {
                        // Take the first element
                        errorObject = errors.getJSONObject(0);
                    }
                } else if (jsonResponse.has("error")) {
                    errorObject = jsonResponse.getJSONObject("error");
                }
                // If an error was set
                if (errorObject != null) {
                    // Build the message
                    final StringBuilder messageBldr = new StringBuilder();
                    final String reason = errorObject.optString("reason");
                    // If there is a valid reason
                    if (StringUtils.hasText(reason)) {
                        // Add it to the message
                        messageBldr.append('[').append(reason).append(']');
                    }
                    final String message = errorObject.optString("message");
                    // If there is a valid message
                    if (StringUtils.hasText(message)) {
                        // If there was content
                        if (messageBldr.length() > 0) {
                            // Add a space
                            messageBldr.append(' ');
                        }
                        // Add it to the message
                        messageBldr.append(message);
                    }
                    // Throw it as an exception
                    throw new RuntimeException(messageBldr.toString());
                }
            } catch (JSONException jsonException) {
                log.error(jsonException.getMessage(), jsonException);
                throw new RuntimeException(jsonException);
            }
    }
}
