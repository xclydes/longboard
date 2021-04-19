package com.xclydes.finance.longboard.svc

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.coroutines.await
import com.xclydes.finance.longboard.config.*
import com.xclydes.finance.longboard.wave.*
import com.xclydes.finance.longboard.wave.GetBusinessAccountsQuery.Accounts
import com.xclydes.finance.longboard.wave.GetBusinessQuery.Business
import com.xclydes.finance.longboard.wave.GetUserQuery.User
import com.xclydes.finance.longboard.wave.type.*
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.*

@Service
class WaveSvc(@Autowired val client: ApolloClient) {

    @Cacheable(cacheNames = [WAVE_USER])
    fun user(): Optional<User> = runBlocking {
        val userResponse = client.query(GetUserQuery()).await()
        return@runBlocking Optional.ofNullable(userResponse.data?.user)
    }

    @Cacheable(cacheNames = [WAVE_BUSINESSES])
    fun businesses(page: Int? = 1, pageSize: Int? = 99): Optional<List<BusinessListQuery.Node>> =
        runBlocking {
            val businessResponse = client.query(
                BusinessListQuery(
                    Input.fromNullable(page), Input.fromNullable(pageSize)
                )
            ).await()
            Optional.ofNullable(businessResponse.data?.businesses?.edges?.mapNotNull { edge -> edge.node })
        }

    @Cacheable(cacheNames = [WAVE_BUSINESS])
    fun business(businessID: String): Optional<Business> = runBlocking {
        val businessResponse = client.query(GetBusinessQuery(businessID))?.await()
        Optional.ofNullable(businessResponse?.data?.business)
    }

    @Cacheable(cacheNames = [WAVE_COUNTRIES])
    fun countries(): Optional<List<CountryListQuery.Country>> = runBlocking {
        val businessResponse = client.query(CountryListQuery())?.await()
        Optional.ofNullable(businessResponse?.data?.countries)
    }

    @Cacheable(cacheNames = [WAVE_ACCOUNT])
    fun businessAccounts(
        businessID: String, page: Int? = 1, pageSize: Int? = 99,
        types: List<AccountTypeValue>? = emptyList(),
        subtypes: List<AccountSubtypeValue>? = emptyList()
    ): Optional<List<GetBusinessAccountsQuery.Node>> = runBlocking{
        val businessResponse = client.query(
            GetBusinessAccountsQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
                Input.fromNullable(types), Input.fromNullable(subtypes)
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.accounts?.edges?.mapNotNull{ e -> e.node})
    }

    @Cacheable(cacheNames = [WAVE_CUSTOMER])
    fun businessCustomers(
        businessID: String,
        page: Int? = 1,
        pageSize: Int? = 99,
        vararg sort: CustomerSort
    ): Optional<List<GetBusinessCustomersQuery.Node>> = runBlocking {
        val businessResponse = client.query(
            GetBusinessCustomersQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
                sort.asList()
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.customers?.edges?.mapNotNull {  it.node })
    }

    fun patchCustomer(customer: CustomerPatchInput): Optional<PatchCustomerMutation.CustomerPatch> = runBlocking {
        val mutationResult = client.mutate(PatchCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerPatch)
    }

    fun createCustomer(customer: CustomerCreateInput): Optional<CreateCustomerMutation.CustomerCreate> = runBlocking {
        val mutationResult = client.mutate(CreateCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerCreate)
    }
}
