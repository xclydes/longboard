package com.xclydes.finance.longboard.svc

import com.Upwork.api.OAuthClient
import com.Upwork.api.Routers.Organization.Companies
import com.Upwork.api.Routers.Organization.Teams
import com.Upwork.api.Routers.Organization.Users
import com.Upwork.api.Routers.Reports.Finance.Earnings
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

@Service
class UpworkSvc(@Autowired val client: OAuthClient,
                @Value("\${longboard.upwork.params.earnings.fields}") val earningsParams: String ) {

    private val companies: Companies by lazy { Companies(client) }
    private val teams: Teams by lazy { Teams(client) }
    private val users: Users by lazy { Users(client) }
    private val earnings: Earnings by lazy { Earnings(client) }
    private val sqlDateFormat: DateFormat by lazyOf( SimpleDateFormat("yyyy-MM-dd"))

    fun user(): JSONObject = users.myInfo

    fun listCompanies(companyId: String?): JSONObject {
        return if (companyId != null) {
            // Get the specific company
            companies.getSpecific(companyId)
        } else {
            // Get the list
            companies.list
        }
    }

    fun listTeams(): JSONObject = teams.list

    fun earnings(entityId: String, from: Date, to:Date,
                fields: String = earningsParams): JSONObject {
        // Build the parameters
        val params = HashMap<String, String>(1)
        .also {
            // Build the select query
            it["tq"] = "SELECT $fields WHERE date >= '${sqlDateFormat.format(from)}' AND date <= '${sqlDateFormat.format(to)}'"
        }
        return earnings.getByFreelancer(entityId, params)
    }

}
