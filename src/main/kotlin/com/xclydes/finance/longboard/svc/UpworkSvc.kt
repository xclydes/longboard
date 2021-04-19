package com.xclydes.finance.longboard.svc

import com.Upwork.api.OAuthClient
import com.Upwork.api.Routers.Organization.Companies
import com.Upwork.api.Routers.Organization.Teams
import com.Upwork.api.Routers.Organization.Users
import com.Upwork.api.Routers.Reports.Finance.Earnings
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.config.UPWORK_COMPANY
import com.xclydes.finance.longboard.config.UPWORK_EARNINGS
import com.xclydes.finance.longboard.config.UPWORK_TEAMS
import com.xclydes.finance.longboard.config.UPWORK_USER
import com.xclydes.finance.longboard.util.JsonUtil.Companion.jsonArrayToList
import com.xclydes.finance.longboard.util.JsonUtil.Companion.toJacksonArray
import com.xclydes.finance.longboard.util.JsonUtil.Companion.toJacksonObject
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@Service
class UpworkSvc(@Autowired val client: OAuthClient,
                @Value("\${longboard.upwork.params.earnings.fields}") val earningsParams: String ) {

    private val companies: Companies by lazy { Companies(client) }
    private val teams: Teams by lazy { Teams(client) }
    private val users: Users by lazy { Users(client) }
    private val earnings: Earnings by lazy { Earnings(client) }
    private val sqlDateFormat: DateFormat by lazyOf( SimpleDateFormat("yyyy-MM-dd"))

    @Cacheable(cacheNames = [UPWORK_USER])
    fun user(): Optional<ObjectNode> = Optional.ofNullable(toJacksonObject(users.myInfo))

    @Cacheable(cacheNames = [UPWORK_COMPANY])
    fun companies(): Optional<ArrayNode> = Optional.ofNullable(toJacksonArray(companies.list.getJSONArray("companies")))

    @Cacheable(cacheNames = [UPWORK_TEAMS])
    fun teams(): Optional<ArrayNode> = Optional.ofNullable(toJacksonArray(teams.list.getJSONArray("teams")))

    @Cacheable(cacheNames = [UPWORK_EARNINGS])
    fun earnings(entityId: String, from: Date, to:Date,
                fields: String = earningsParams): ArrayNode {
        // Build the parameters
        val params = HashMap<String, String>(1)
        .also {
            // Build the select query
            it["tq"] = "SELECT $fields WHERE date >= '${sqlDateFormat.format(from)}' AND date <= '${sqlDateFormat.format(to)}'"
        }
        // Re-map the entries
        val earningResponse = earnings.getByFreelancer(entityId, params)
        val earnings = JSONArray()
        // Extract the table
        if (earningResponse.has("table")) {
            val table: JSONObject = earningResponse.getJSONObject("table")
            // Get the heading
            val headings = jsonArrayToList(table.getJSONArray("cols"))
                .map { col -> col.getString("label") }
                .toList()
            // Process each row
            jsonArrayToList(table.getJSONArray("rows")).stream()
                .map { row -> row.getJSONArray("c") }
                .map { valueArr -> jsonArrayToList(valueArr).map { vJson -> vJson.getString("v") } }
                .forEach { valueList ->
                    with(JSONObject()) {
                        valueList.onEachIndexed { index, value -> this.put(headings[index], value) }
                        earnings.put(this)
                    }
                }
        }
        return toJacksonArray(earnings);
    }
}
