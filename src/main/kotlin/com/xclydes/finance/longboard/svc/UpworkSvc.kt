package com.xclydes.finance.longboard.svc

import com.Upwork.api.OAuthClient
import com.Upwork.api.Routers.Organization.Companies
import com.Upwork.api.Routers.Organization.Teams
import com.Upwork.api.Routers.Organization.Users
import com.Upwork.api.Routers.Reports.Finance.Accounts
import com.Upwork.api.Routers.Reports.Finance.Earnings
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.config.*
import com.xclydes.finance.longboard.util.JsonUtil
import com.xclydes.finance.longboard.util.JsonUtil.Companion.jsonArrayToList
import com.xclydes.finance.longboard.util.JsonUtil.Companion.toJacksonArray
import com.xclydes.finance.longboard.util.JsonUtil.Companion.toJacksonObject
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

@Service
class UpworkSvc(@Autowired val client: OAuthClient,
                @Value("\${longboard.upwork.params.earnings.fields}") val earningsParams: String,
                @Value("\${longboard.upwork.params.account-ref}") val accountRef: String) {

    companion object {
        val dateFormatSQL: DateFormat = SimpleDateFormat("yyyy-MM-dd")
        val dateFormatReport: DateFormat = SimpleDateFormat("yyyyMMdd")
        val dateFormatDescription: DateFormat = SimpleDateFormat("MM/dd/yyyy")
        val patternInvoiceDescription = Pattern.compile("^\\((.+)\\) ([^-\\s]+) - (\\d{1,2}):(\\d{2})\\shrs @ \\\$([^/]*)/hr - ([\\d-/]*) - ([\\d-/]*)\$")
    }

    private val companies: Companies by lazy { Companies(client) }
    private val accounts: Accounts by lazy { Accounts(client) }
    private val teams: Teams by lazy { Teams(client) }
    private val users: Users by lazy { Users(client) }
    private val earnings: Earnings by lazy { Earnings(client) }

    private fun remapTable(response: JSONObject): ArrayNode {
        val earnings = JsonUtil.objectReader.createArrayNode() as ArrayNode
        // Extract the table
        if (response.has("table")) {
            val table: JSONObject = response.getJSONObject("table")
            // Get the heading
            val headings = jsonArrayToList(table.getJSONArray("cols"))
                .map { col -> col.getString("label") }
                .toList()
            // Process each row
            jsonArrayToList(table.getJSONArray("rows")).stream()
                .map { row -> row.getJSONArray("c") }
                .map { valueArr -> jsonArrayToList(valueArr).map { vJson -> vJson.getString("v") } }
                .forEach { valueList ->
                    with(JsonUtil.newObject()) {
                        valueList.onEachIndexed { index, value -> this.put(headings[index], value) }
                        earnings.add(this)
                    }
                }
        }
        return earnings
    }

    @Cacheable(cacheNames = [UPWORK_USER])
    fun user(): Optional<ObjectNode> = Optional.ofNullable(toJacksonObject(users.myInfo.getJSONObject("user")))

    @Cacheable(cacheNames = [UPWORK_COMPANY])
    fun companies(): Optional<ArrayNode> = Optional.ofNullable(toJacksonArray(companies.list.getJSONArray("companies")))

    @Cacheable(cacheNames = [UPWORK_TEAMS])
    fun teams(): Optional<ArrayNode> = Optional.ofNullable(toJacksonArray(teams.list.getJSONArray("teams")))

    @Cacheable(cacheNames = [UPWORK_EARNINGS])
    fun earnings(from: Date, to:Date,
                 userReference: String = "",
                 fields: String = earningsParams,
                 ): ArrayNode {
        var resolvedUserReference = userReference
        if(userReference.isEmpty()) {
            user().ifPresent{ user -> resolvedUserReference = user.required("reference").textValue()}
        }
        // If a valid reference is not found
        if (resolvedUserReference.isEmpty()) throw IllegalStateException("Unable to determine user reference")
        // Build the parameters
        val params = HashMap<String, String>(1)
        .also {
            // Build the select query
            it["tq"] = "SELECT $fields WHERE date >= '${dateFormatSQL.format(from)}' AND date <= '${dateFormatSQL.format(to)}'"
        }
        // Re-map the entries
        val earningResponse = earnings.getByFreelancer(resolvedUserReference, params)
        // Flatten the table
        return remapTable(earningResponse);
    }

    @Cacheable(cacheNames = [UPWORK_ACCOUNTING])
    fun accountsForEntity(from: Date, to:Date,
                 acctRef: String,
                 fields: String = earningsParams,
                 ): ArrayNode {
        // If a valid reference is not found
        if (acctRef.isEmpty()) throw IllegalStateException("A valid account reference is required")
        // Build the parameters
        val params = HashMap<String, String>(1)
        .also {
            // Build the select query
            it["tq"] = "SELECT $fields WHERE date >= '${dateFormatSQL.format(from)}' AND date <= '${dateFormatSQL.format(to)}'"
        }
        // Re-map the entries
        val accountResponse = accounts.getSpecific(acctRef, params)
        // Flatten the table
        return remapTable(accountResponse);
    }

    @Cacheable(cacheNames = [UPWORK_ACCOUNTS])
    fun accountsForUser(from: Date, to:Date,
                        userReference: String,
                        fields: String = earningsParams): ArrayNode {
        var resolvedUserReference = userReference
        if(userReference.isEmpty()) {
            user().ifPresent{ user -> resolvedUserReference = user.required("reference").textValue()}
        }
        // If a valid reference is not found
        if (resolvedUserReference.isEmpty()) throw IllegalStateException("Unable to determine user reference")
        // Build the parameters
        val params = HashMap<String, String>(1)
        .also {
            // Build the select query
            it["tq"] = "SELECT $fields WHERE date >= '${dateFormatSQL.format(from)}' AND date <= '${dateFormatSQL.format(to)}'"
        }
        // Re-map the entries
        val accountResponse = accounts.getOwned(userReference, params)
        // Flatten the table
        return remapTable(accountResponse);
    }
}
