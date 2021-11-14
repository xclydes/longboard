package com.xclydes.finance.longboard.upwork;

import com.Upwork.api.OAuthClient;
import com.Upwork.api.Routers.Hr.Engagements;
import com.Upwork.api.Routers.Jobs.Profile;
import com.Upwork.api.Routers.Organization.Companies;
import com.Upwork.api.Routers.Organization.Teams;
import com.Upwork.api.Routers.Organization.Users;
import com.Upwork.api.Routers.Reports.Finance.Accounts;
import com.Upwork.api.Routers.Reports.Finance.Billings;
import com.Upwork.api.Routers.Reports.Finance.Earnings;
import com.Upwork.api.Routers.Reports.Time;
import com.Upwork.api.Routers.Workdiary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Pagination;
import com.xclydes.finance.longboard.models.RequestToken;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.models.*;
import com.xclydes.finance.longboard.util.ArrayUtil;
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
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final List<String> TimeReportFields = List.of(
            "provider_id", "provider_name", "assignment_team_id", "assignment_name","assignment_ref",
            "agency_id","agency_name","company_id","agency_company_id","task","memo","hours",
            "charges","hours_online","charges_online","hours_offline","charges_offline",
            "worked_on","week_worked_on","month_worked_on","year_worked_on");
    private static final List<String> FinanceReportFields = List.of(
            "reference", "date", "date_due", "assignment__reference", "assignment_name",
            "accounting_entity__reference", "accounting_entity_name", "buyer_company__reference",
            "buyer_company__id", "buyer_company_name", "buyer_team__reference", "buyer_team__id", "buyer_team_name",
            "provider_company__reference", "provider_company__id", "provider_company_name", "provider_team__reference",
            "provider_team__id", "provider_team_name", "provider__reference", "provider__id", "provider_name",
            "type", "subtype", "description", "comment", "memo", "notes", "amount", "po_number"
            );

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
     * Gets the profile of the current user or one with the profileKey given if any
     *
     * @param token The access token to be used
     * @return The user requested, if found
     */
    @Cacheable(cacheNames = {UPWORK_PROFILE})
    public List<User.Profile> profile(final Token token, final String ... profileKey) {
        // Filter the list
        final String resolvedProfileKey = Stream.of(profileKey)
                .filter(StringUtils::hasText)
                .reduce((p,v) -> p +','+v)
                .orElseThrow(() -> new IllegalArgumentException("At least one (1) valid profile key is required"));
        // Initialize the user function
        final Profile profileRoute = new Profile(getClientProvider().getClient(token));
        try {
            final JSONObject myInfoResponse = profileRoute.getSpecific(resolvedProfileKey);
            // Check for errors
            checkForException(myInfoResponse);
            final JSONArray profilesJson = myInfoResponse.getJSONArray("profiles");
            return fromJsonArray(profilesJson, User.Profile.class);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
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

    @Cacheable(cacheNames = {UPWORK_COMPANY_TEAMS})
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

    @Cacheable(cacheNames = {UPWORK_COMPANY_WORKDIARY})
    public List<DiaryRecord> companyWorkdiary(final Token token,
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
     * @param userRef   The reference for the freelancer
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_USER})
    public List<FinanceRecord> earningsForUser(final Token token,
                                            final LocalDate from,
                                            final LocalDate to,
                                            final String userRef
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedRef = resolveUserReference(token, userRef);
        // Disallowed fields: `comment`, `po_number`.
        // Supported filters: `date`, `week`, `month`, `year`, `date_due`, `buyer_company__reference`,
        // `buyer_company__id`, `buyer_team__reference`, `buyer_team__id`, `provider_company__reference`,
        // `provider_company__id`, `assignment__reference`, `type`, `subtype`.
        // Permissions: freelancer.
        return performFinancialQuery(
                QueryBuilder
                    .get(ArrayUtil.without(FinanceReportFields, "comment", "po_number"))
                    .andWhere("date", ">=", from)
                    .andWhere("date", "<=", to)
                    .build(),
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
     * @param userRef   The reference for the freelancer
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_BILLING_USER})
    public List<FinanceRecord> billingsForUser(final Token token,
                                               final LocalDate from,
                                               final LocalDate to,
                                               final String userRef
    ) {
        // Use the user provided, or the one the token belongs to
        final String resolvedRef = resolveUserReference(token, userRef);
        // Disallowed fields: `comment`, `po_number`.
        // Supported filters: `date`, `week`, `month`, `year`, `date_due`,
        // `buyer_company__reference`, `buyer_company__id`, `buyer_team__reference`, `buyer_team__id`,
        // `provider_company__reference`, `provider_company__id`, `assignment__reference`, `type`, `subtype`.
        // Permissions: Exclusive Agency Member
        return performFinancialQuery(
                QueryBuilder
                    .get(ArrayUtil.without(FinanceReportFields, "comment", "po_number"))
                    .andWhere("date", ">=", from)
                    .andWhere("date", "<=", to)
                    .build(),
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
     * @param companyRef  The reference ID of the company's parent team the authenticated user has access to.
     *                    The authenticated user must be the owner of the company.
     *                    Use Teams & Companies resource to get it.
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_BILLINGS_FREELANCER_COMPANY})
    public List<FinanceRecord> billingsForFreelancerCompany(final Token token,
                                                         final LocalDate from,
                                                         final LocalDate to,
                                                         final String companyRef
    ) {
        throw new IllegalStateException("This function is no longer available. See https://developers.upwork.com/api-changelog.html#thursday-2018-06-28.");

        // If a valid reference is not found
//        final String resolvedRef = ValidationUtil.requires(companyRef,
//                StringUtils::hasText, "A valid freelancer/company reference is required");

        //  Disallowed fields: `comment`, `po_number`.
        //  Supported filters: `date`, `week`, `month`, `year`, `date_due`, `buyer_company__reference`,
        //  `buyer_company__id`, `buyer_team__reference`, `buyer_team__id`, `provider__reference`,
        //  `provider__id`, `assignment__reference`, `accounting_entity__reference`, `type`, `subtype`.
        //  Permissions: owner or admin.
//        return performFinancialQuery(
//                QueryBuilder
//                        .get(ArrayUtil.without(FinanceReportFields, "comment", "po_number"))
//                        .andWhere("date", ">=", from)
//                        .andWhere("date", "<=", to)
//                        .build(),
//                (params) -> {
//                    try {
//                        return getBillingsRoute(token).getByFreelancersCompany(resolvedRef, params);
//                    } catch (JSONException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//        );
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
    public List<FinanceRecord> earningsForFreelancerTeam(final Token token,
                                                      final LocalDate from,
                                                      final LocalDate to,
                                                      final String ref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(ref,
                StringUtils::hasText, "A valid freelancer/team reference is required");
        return performFinancialQuery(
                QueryBuilder
                        .get(FinanceReportFields)
                        .andWhere("date", ">=", from)
                        .andWhere("date", "<=", to)
                        .build(),
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
     * @param clientTeamRef The reference ID of the team the authenticated user has access to.
     *                      The authenticated user must be an admin or a staffing manager of the team.
     *                      Use Companies & Teams resource to get it
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_TEAM})
    public List<FinanceRecord> earningsForBuyersTeam(final Token token,
                                                     final LocalDate from,
                                                     final LocalDate to,
                                                     final String clientTeamRef
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(clientTeamRef,
                StringUtils::hasText, "A valid buyers/team reference is required");
        //Supported filters: `date`, `week`, `month`, `year`, `date_due`, `provider_team__reference`,
        // `provider_team__id`, `provider__reference`, `provider__id`, `assignment__reference`, `type`,
        // `subtype`, `po_number`.
        // Permissions: hiring.
        return performFinancialQuery(
                QueryBuilder
                        .get(FinanceReportFields)
                        .andWhere("date", ">=", from)
                        .andWhere("date", "<=", to)
                        .build(),
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
     * Gets Finance records for a specific client team
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param clientTeamRef The reference ID of the team the authenticated user has access to.
     *                      The authenticated user must be an admin or a staffing manager of the team.
     *                      Use Companies & Teams resource to get it
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_BILLINGS_BUYER_TEAM})
    public List<FinanceRecord> billingsForBuyersTeam(final Token token,
                                                     final LocalDate from,
                                                     final LocalDate to,
                                                     final String clientTeamRef
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(clientTeamRef,
                StringUtils::hasText, "A valid buyers/team reference is required");
        return performFinancialQuery(
                QueryBuilder
                        .get(FinanceReportFields)
                        .andWhere("date", ">=", from)
                        .andWhere("date", "<=", to)
                        .build(),
                    (params) -> {
                        try {
                            return getBillingsRoute(token).getByBuyersTeam(resolvedRef, params);
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
     * @param companyRef   The reference of the company to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_EARNINGS_BUYER_COMPANY})
    public List<FinanceRecord> earningsForBuyersCompany(final Token token,
                                                     final LocalDate from,
                                                     final LocalDate to,
                                                     final String companyRef
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(companyRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        return performFinancialQuery(
                QueryBuilder
                        .get(FinanceReportFields)
                        .andWhere("date", ">=", from)
                        .andWhere("date", "<=", to)
                        .build(),
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
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @param buyerCompanyRef The reference ID of the company the authenticated user has access to.
     *                        The authenticated user must be the owner of the company.
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_BILLINGS_BUYER_COMPANY})
    public List<FinanceRecord> billingsForBuyersCompany(final Token token,
                                                     final LocalDate from,
                                                     final LocalDate to,
                                                     final String buyerCompanyRef
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(buyerCompanyRef,
                StringUtils::hasText, "A valid buyers/company reference is required");
        return performFinancialQuery(
                QueryBuilder
                        .get(FinanceReportFields)
                        .andWhere("date", ">=", from)
                        .andWhere("date", "<=", to)
                        .build(),
                    (params) -> {
                        try {
                            return getBillingsRoute(token).getByBuyersCompany(resolvedRef, params);
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
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_TIME_USER})
    public List<TimeRecord> timeByUser(final Token token,
                                         final LocalDate from,
                                         final LocalDate to
    ) {
        // Get the user for the token
        final User user = this.user(token, null)
                .orElseThrow(() -> new IllegalStateException("Unable to determine username"));
        // Fields that cannot appear in the query: `provider_id`, `provider_name`, `charges`,
        // `charges_online`, `charges_offline`.
        return performTimeQuery(
                QueryBuilder
                        .get(ArrayUtil.without(TimeReportFields,"provider_id", "provider_name", "charges",
                                "charges_online", "charges_offline"))
                        .andWhere("worked_on", ">=", from)
                        .andWhere("worked_on", "<=", to)
                        .build(),
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByFreelancerFull(user.id, params);
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
     * @param companyId   The reference of the company to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_TIME_COMPANY})
    public List<TimeRecord> timeByCompany(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyId
    ) {
        // If a valid reference is not found
        final String resolvedCompanyId = ValidationUtil.requires(companyId,
                StringUtils::hasText, "A valid buyers/company reference is required");
        // Fields that cannot appear in the query: `company_id`
        return performTimeQuery(
                QueryBuilder
                        .get(ArrayUtil.without(TimeReportFields,"company_id"))
                        .andWhere("worked_on", ">=", from)
                        .andWhere("worked_on", "<=", to)
                        .build(),
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByCompany(resolvedCompanyId, params);
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
     * @param companyId   The reference of the company to be queried
     * @param agencyId   The reference of the agency to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_TIME_AGENCY})
    public List<TimeRecord> timeByAgency(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyId,
                                         final String agencyId
    ) {
        // If a valid reference is not found
        final String resolvedCompany = ValidationUtil.requires(companyId,
                StringUtils::hasText, "A valid buyers/company reference is required");
        // If a valid reference is not found
        final String resolvedAgency = ValidationUtil.requires(agencyId,
                StringUtils::hasText, "A valid agency reference is required");
        // Fields that cannot appear in the query: `agency_id`, `agency_name`,
        // `charges`, `charges_online`, `charges_offline`.
        return performTimeQuery(
                QueryBuilder
                        .get(ArrayUtil.without(TimeReportFields,"agency_id", "agency_name", "agency_company_id", "charges", "charges_online", "charges_offline"))
                        .andWhere("worked_on", ">=", from)
                        .andWhere("worked_on", "<=", to)
                        .build(),
                    (params) -> {
                        try {
                            return getTimeRoute(token).getByAgency(resolvedCompany, resolvedAgency, params);
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
     * @param companyId   The reference of the company to be queried
     * @param teamId   The reference of the team to be queried
     * @return The list of earning records found
     */
    @Cacheable(cacheNames = {UPWORK_TIME_TEAM})
    public List<TimeRecord> timeByTeam(final Token token,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final String companyId,
                                         final String teamId
    ) {
        // If a valid reference is not found
        final String resolvedCompanyRef = ValidationUtil.requires(companyId,
                StringUtils::hasText, "A valid company id is required");
        final String resolvedTeamRef = ValidationUtil.requires(teamId,
                StringUtils::hasText, "A valid team is required");
        // Fields that cannot appear in the query: `team_id`, `team_name`
        return performTimeQuery(
                QueryBuilder
                        .get(ArrayUtil.without(TimeReportFields,"team_id", "team_name"))
                        .andWhere("worked_on", ">=", from)
                        .andWhere("worked_on", "<=", to)
                        .build(),
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
     * @param accountingEntityref   The reference ID of an accounting entity. Example: `34567`.
     *              You need to contact Support Team in order to receive it - it remains unchangeable
     * @return The list of account records found
     */
    @Cacheable(cacheNames = {UPWORK_ACCOUNTING_ENTITY})
    public List<FinanceRecord> accountsForEntity(final Token token,
                                              final LocalDate from,
                                              final LocalDate to,
                                              final String accountingEntityref
    ) {
        // If a valid reference is not found
        final String resolvedRef = ValidationUtil.requires(accountingEntityref,
                StringUtils::hasText, "A valid account reference is required");
        // Disallowed fields: none.
        // Supported filters: `date`, `week`, `month`, `year`, `date_due`,
        // `provider_company__reference`, `provider_company__id`, `provider__reference`, `provider__id`,
        // `buyer_company__reference`, `buyer_company__id`, `buyer_team__reference`, `buyer_team__id`,
        // `assignment__reference`, `type`, `subtype`, `po_number`, `provider_team__reference`,
        // `provider_team__id`.
        // Permissions: finance manager.
        return performFinancialQuery(
                QueryBuilder
                    .get(FinanceReportFields)
                    .andWhere("date", ">=", from)
                    .andWhere("date", "<=", to)
                    .build(),
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
                QueryBuilder
                    .get(FinanceReportFields)
                    .andWhere("date", ">=", from)
                    .andWhere("date", "<=", to)
                    .build(),
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
     * Gets engagement records
     *
     * @param token The access token to be used
     * @param from  The start date to query
     * @param to    The end date to stop querying at
     * @return The list of account records found
     */
    @Cacheable(cacheNames = {UPWORK_ENGAGEMENTS})
    public DataPage<EngagementRecord> engagements(final Token token,
                                                  final LocalDate from,
                                                  final LocalDate to,
                                                  final Integer pageIn,
                                                  final Integer pageSizeIn,
                                                  final String status) {
        // Gather the parameters
        final HashMap<String, String> params = new HashMap<>();
        // If there is a from
        if(from != null) {
            params.put("created_time_from", dateFormatSQL.format(from.atStartOfDay()));
        }
        // If there is a to
        if(to != null) {
            params.put("created_time_to", dateFormatSQL.format(to.atTime(23, 59, 59)));
        }
        // If there is a status filter
        if(StringUtils.hasText(status)) {
            params.put("status", status);
        }
        // Resolve some sensible defaults for the paging
        final int page = pageIn != null ? pageIn : 1;
        final int pageSize = pageSizeIn != null ? pageSizeIn : 10;
        // Upwork expects offset,size
        params.put("page", String.format("%d;%d", Math.max(0, page - 1) * pageSize, pageSize));
        try {
            final Engagements engagementRoute = new Engagements(getClientProvider().getClient(token));
            final JSONObject engagementsListResponse = engagementRoute.getList(params);
            // Check for errors
            checkForException(engagementsListResponse);
            final JSONObject engagementsResponseObj = engagementsListResponse.getJSONObject("engagements");
            final JSONArray engagementsArr = engagementsResponseObj.getJSONArray("engagement");
            // Replace all empty string with null
            JsonUtil.jsonArrayToList(engagementsArr).forEach(UpworkSvc::treatEmptyStringsAsNulls);
            // Convert to a proper list of records
            final List<EngagementRecord> engagementRecordList = fromJsonArray(engagementsArr, EngagementRecord.class);
            //Get the paging data
            final JSONObject engagmentLister = engagementsResponseObj.getJSONObject("lister");
            final double totalEngagements = engagmentLister.getDouble("total_count");
            // Convert the page data
            final Pagination pagination = new Pagination(
                    pageSize,
                    page,
                    Double.valueOf(Math.ceil(totalEngagements / pageSize)).intValue(),
                    Double.valueOf(totalEngagements).intValue()
            );
            return new DataPage<>(pagination, engagementRecordList);
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
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
     * Performs a query to fetch accounting related activities from Upwork.
     *
     * @param query The query to be run
     * @param exec The callback to perform the query execution and extract the appropriate result.
     *             By result that means a typical report representation which can be mapped to a list of Accounting
     *             records.
     * @return The list of records found, if any.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_financial-reports-fields">Financial Reports</a>
     */
    private List<FinanceRecord> performFinancialQuery(final String query,
                                                      final Function<HashMap<String, String>, JSONObject> exec) {
        return performQuery(query, FinanceRecord.class, exec);
    }

    /**
     * Performs a query to fetch time related activities from Upwork.
     * Where conditions are only allowed on company_id, agency_id, provider_id, worked_on, assignment_team_id, task.
     *
     * @param query            The query to be executed
     * @param exec             The callback to perform the query execution and extract the appropriate result.
     *                         By result that means a typical report representation which can be mapped to a list of Earning
     *                         records.
     * @return The list of records found, if any.
     * @see <a href="https://developers.upwork.com/?lang=node#reports_time-reports-fields">Time Reports</a>
     */
    private List<TimeRecord> performTimeQuery(final String query,
                                              final Function<HashMap<String, String>, JSONObject> exec) {
        return performQuery(query, TimeRecord.class, exec);
    }

    private <T> List<T> performQuery(final String query,
                                     final Class<T> resultClass,
                                     final Function<HashMap<String, String>, JSONObject> executor) {
        // Build the parameters
        final HashMap<String, String> params = new HashMap<>(1);
        // Build the select query
        params.put("tq", query);
        // Execute th request
        final JSONObject qryResponse = executor.apply(params);
        // Check for errors
        checkForException(qryResponse);
        // Flatten the table
        final ArrayNode remappedTable = remapTable(qryResponse);
        // Process to the required class
        return fromJsonArray(remappedTable, resultClass);
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

    private static Collection<String> treatEmptyStringsAsNulls(final JSONObject obj) {
        final Collection<String> removed = new ArrayList<>();
        // Process the keys
        final List<String> keys = new ArrayList<>();
        obj.keys().forEachRemaining(k -> keys.add(String.valueOf(k)));
        keys.forEach((key) -> {
            try {
                final Object value = obj.get(key);
                if( value instanceof String && !StringUtils.hasText((String) value)) {
                    obj.remove(key);
                    removed.add(key);
                }
            } catch (JSONException e) {}
        });
        return removed;
    }

    /**
     * Processes a table-like response from Upwork into an array node of
     * remapped objects.
     * Typically of the format {"table" : {"cols" : [], rows: {"c" : []}}}
     * @param response The response from upwork to processed.
     * @return The remapped table data
     */
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
