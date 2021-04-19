package com.xclydes.finance.longboard.upwork

import com.Upwork.api.Config
import com.Upwork.api.OAuthClient
import com.Upwork.api.Routers.Hr.Engagements
import com.Upwork.api.Routers.Organization.Companies
import com.Upwork.api.Routers.Organization.Teams
import com.Upwork.api.Routers.Organization.Users
import com.Upwork.api.Routers.Reports.Finance.Accounts
import com.Upwork.api.Routers.Reports.Finance.Billings
import com.Upwork.api.Routers.Reports.Finance.Earnings
import com.Upwork.api.Routers.Reports.Time
import org.json.JSONObject
import java.net.URLDecoder
import java.util.*
import kotlin.collections.HashMap
import kotlin.streams.toList


fun main() {
    print("Enter the Client Key : ")
    val consumerKey = readLine();
    print("Enter the Client Secret : ")
    val consumerSecret = readLine();

    val keys = Properties()
    keys.setProperty("consumerKey", consumerKey)
    keys.setProperty("consumerSecret", consumerSecret)

    // Collect the access token and secret if available
    print("Enter your token. Leave blank to skip : ")
    val tokenValue = readLine()?.trim();
    val hasToken = tokenValue != null && tokenValue.isNotBlank()
    var hasSecret = false
    var tokenSecret: String? = ""
    if(hasToken) {
        // Prompt for the secret
        print("Enter your secret. Leave blank to discard previous token : ")
        tokenSecret = readLine()?.trim()
        hasSecret = tokenSecret != null && tokenSecret.isNotBlank()
    }

    val config = Config(keys)
    val client = OAuthClient(config)
    // If there is no token
    if( !hasSecret || !hasToken) {
        val authzUrl = client.authorizationUrl

        println("===")
        println("1. Copy paste the following url in your browser : ")
        println(authzUrl)
        println("2. Grant access ")
        print("3. Copy paste the oauth_verifier parameter here : ")

        val oauth_verifier = readLine()
        var verifier: String? = null
        try {
            verifier = URLDecoder.decode(oauth_verifier, "UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val token = client.getAccessTokenSet(verifier)
        println("=====")
        println("Token: %s".format(token))
        println("=====")
        // Update the client
        client.setTokenWithSecret(token["token"], token["secret"])
    } else {
        client.setTokenWithSecret(tokenValue, tokenSecret)
    }

    // Create an authenticated context
    //val auth = Auth(client)
    // Present the activity menu
    var processInput = true
    while(processInput) {
        println("=====")
        println("1. Get User")
        println("2. Companies")
        println("3. Teams")
        println("4. Engagements")
        println("5. Accounts")
        println("6. Time")
        println("7. Billing")
        println("8. Earnings")
        println("0. Exit")
        print("Select an activity [1-99]: ")
        val option = readLine()
        when(option?.toInt()) {
            1 -> getUser( client )
            2 -> listCompanies( client )
            3 -> listTeams( client )
            4 -> listEngagements( client )
            5 -> accountsReport( client )
            6 -> timeReport( client )
            7 -> billingReport( client )
            8 -> earningReport( client )
            else -> processInput = false
        }
        println("=====")
    }
}

fun promptForParams(helpUrl: String): HashMap<String, String> {
    // Build the parameters list
    val params = HashMap<String, String>()
    var collect = true;
    println("Enter parameters <name>=<value>. See %s".format(helpUrl))
    while( collect ) {
        val input = readLine().orEmpty().trim()
        if(input.isNotEmpty()) {
            val (paramName, paramValue) = input
                .split("=", ignoreCase = true ,limit = 2)
                .stream().map { it.trim() }.toList()
            // Add it to the map
            params[paramName] = paramValue;
        } else {
            collect = false
        }
    }
    return params
}

fun getUser(client: OAuthClient) {
    // Get and print the user info
    val userInfo = Users(client)
    println("Logged in User: %s".format(userInfo.myInfo.toString(5)))
}

fun listCompanies(client: OAuthClient) {
    val router = Companies(client)
    print("Enter a company ID. Leave blank for all :")
    val companyId = readLine().orEmpty().trim();
    var companyResult: JSONObject
    if(companyId.isNotEmpty()) {
        // Get the specific company
        companyResult = router.getSpecific(companyId)
    } else {
        // Get the list
        companyResult = router.list
    }
    println("Company(ies): %s".format(companyResult.toString(5)))
}

fun listTeams(client: OAuthClient) {
    val router = Teams(client)
    var queryResult: JSONObject = router.list
    println("Team(s): %s".format(queryResult.toString(5)))
}

fun listEngagements(client: OAuthClient) {
    val router = Engagements(client)
    print("Enter a engagement ID. Leave blank for all : ")
    val engagementId = readLine().orEmpty().trim();
    var queryResult: JSONObject
    // If there is an ID
    if(engagementId.isNotEmpty()) {
        queryResult = router.getSpecific( engagementId )
    } else {
        // Build the parameters list
        val params = promptForParams("https://developers.upwork.com/?lang=java#contracts-and-offers_list-engagements")
        println("Querying engagements with: %s".format(params))
        queryResult = router.getList( params )
    }
    println("Engagement(s): %s".format(queryResult.toString(5)))
}

fun accountsReport(client: OAuthClient) {
    val router = Accounts(client)
    print("Enter a entity ID : ")
    val entityId = readLine().orEmpty().trim();
    // If there is an ID
    if(entityId.isEmpty()) {
        println("A valid entity ID is required")
    } else {
        // Build the parameters list
        val params = promptForParams("https://developers.upwork.com/?lang=java#reports_get-financial-reports-for-an-account")
        println("Querying account for %s with: %s".format(entityId, params))
        val queryResult = router.getSpecific( entityId, params )
        println("Accounts: %s".format(queryResult.toString(5)))
    }
}

fun timeReport(client: OAuthClient) {
    val router = Time(client)
    print("Enter a entity ID : ")
    val entityId = readLine().orEmpty().trim();
    // If there is an ID
    if(entityId.isEmpty()) {
        println("A valid entity ID is required")
    } else {
        // Build the parameters list
        val params = promptForParams("https://developers.upwork.com/?lang=java#reports_get-financial-reports-for-an-account")
        println("Querying account for %s with: %s".format(entityId, params))
//        val queryResult = router.getByFreelancerLimited( entityId, params )
        val queryResult = router.getByFreelancerFull( entityId, params )
        println("Time: %s".format(queryResult.toString(5)))
    }
}

fun billingReport(client: OAuthClient) {
    val router = Billings(client)
    print("Enter a entity ID : ")
    val entityId = readLine().orEmpty().trim();
    // If there is an ID
    if(entityId.isEmpty()) {
        println("A valid entity ID is required")
    } else {
        // Build the parameters list
        val params = promptForParams("https://developers.upwork.com/?lang=java#reports_get-financial-reports-for-an-account")
        println("Querying account for %s with: %s".format(entityId, params))
//        val queryResult = router.getByFreelancerLimited( entityId, params )
        val queryResult = router.getByFreelancer( entityId, params )
        println("Accounts: %s".format(queryResult.toString(5)))
    }
}

fun earningReport(client: OAuthClient) {
    val router = Earnings(client)
    print("Enter a entity ID : ")
    val entityId = readLine().orEmpty().trim();
    // If there is an ID
    if(entityId.isEmpty()) {
        println("A valid entity ID is required")
    } else {
        // Build the parameters list
        val params = promptForParams("https://developers.upwork.com/?lang=java#reports_get-earning-reports-for-a-freelancer")
        println("Querying earnings for %s with: %s".format(entityId, params))
//        val queryResult = router.getByFreelancerLimited( entityId, params )
        val queryResult = router.getByFreelancer( entityId, params )
        println("Earnings: %s".format(queryResult.toString(5)))
    }
}
