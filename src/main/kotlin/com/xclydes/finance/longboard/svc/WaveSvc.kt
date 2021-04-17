package com.xclydes.finance.longboard.svc

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.coroutines.await
import com.xclydes.finance.longboard.wave.BusinessListQuery
import com.xclydes.finance.longboard.wave.CountryListQuery
import com.xclydes.finance.longboard.wave.GetUserQuery
import com.xclydes.finance.longboard.wave.GetUserQuery.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class WaveSvc(@Autowired val client:ApolloClient) {

    suspend fun user(): Optional<User> {
        val userResponse = client.query(GetUserQuery()).await()
        return Optional.ofNullable(userResponse.data?.user)
    }

    suspend fun listBusinesses(): Optional<List<BusinessListQuery.Edge>> {
        val businessResponse = client.query(BusinessListQuery(
            Input.fromNullable(1), Input.fromNullable(99))
        )?.await()
        val businessPage = businessResponse?.data?.businesses
        val businesses: List<BusinessListQuery.Edge>? = businessPage?.edges
        return Optional.ofNullable(businesses)
    }

    suspend fun listCountries(): Optional<List<CountryListQuery.Country>> {
        val businessResponse = client.query(CountryListQuery())?.await()
        return Optional.ofNullable(businessResponse?.data?.countries)
    }
}
