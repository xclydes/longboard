package com.xclydes.finance.longboard.svc

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.api.toInput
import com.apollographql.apollo.coroutines.await
import com.xclydes.finance.longboard.config.*
import com.xclydes.finance.longboard.wave.*
import com.xclydes.finance.longboard.wave.GetBusinessQuery.Business
import com.xclydes.finance.longboard.wave.GetUserQuery.User
import com.xclydes.finance.longboard.wave.type.*
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.util.Base64Utils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

@Service
class WaveSvc(@Autowired val client: ApolloClient,
              @Value("\${longboard.wave.token}") token: String) {

    companion object {
        const val ID_BUSINESS: String = "Business";
        const val ID_PRODUCT: String = "Product";
        const val ID_ACCOUNT: String = "Account";

        val inputDateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")
        val reportDateFormat: DateFormat = SimpleDateFormat("yyyyMMdd")
    }

    private val restClient by lazy { RestTemplateBuilder().defaultHeader("Authorization", "Bearer ${token}").build() }

    @Cacheable(cacheNames = [WAVE_APIID])
    fun decodeId(b64Id: String) : Map<String, String> =
        Base64Utils
            .decodeFromString(b64Id)
            .decodeToString()
            .split(delimiters = arrayOf(";", ":"))
            .zipWithNext()
            .toMap()

    @Cacheable(cacheNames = [WAVE_USER])
    fun user(): Optional<User> = runBlocking {
        val userResponse = client.query(GetUserQuery()).await()
        return@runBlocking Optional.ofNullable(userResponse.data?.user)
    }

    /* Start Business */
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
    /* Start Business */

    /* Start Invoices */
    @Cacheable(cacheNames = [WAVE_INVOICES])
    fun invoices(businessID: String,
                 from: Date = Date.from(Instant.EPOCH), to:Date = Date(),
                 page: Int = 1, pageSize: Int = 99,
                 invoiceRef: String? = null
    ) : List<GetBusinessInvoicesQuery.Node>? = runBlocking {
        // No from is set
        val invoicesResponse = client.query(
            GetBusinessInvoicesQuery(businessID,
                filterFrom = inputDateFormat.format(from).toInput(),
                filterTo = inputDateFormat.format(to).toInput(),
                invPage = page.toInput(),
                invPageSize = pageSize.toInput(),
                filterNum = Input.optional(invoiceRef)
            )
        ).await()
        invoicesResponse.data?.business?.invoices?.edges?.map { edge -> edge.node }
    }

    @Cacheable(cacheNames = [WAVE_INVOICE])
    fun invoice(businessID: String, invoiceID: String) : GetBusinessInvoiceQuery.Invoice? = runBlocking {
        // No from is set
        val invoicesResponse = client.query(
            GetBusinessInvoiceQuery(businessID, invoiceID)
        ).await()
        invoicesResponse.data?.business?.invoice
    }

    @CacheEvict(WAVE_INVOICE, WAVE_INVOICES)
    fun createInvoice(invoice: InvoiceCreateInput): CreateInvoiceMutation.InvoiceCreate? = runBlocking {
        val mutationResult = client.mutate(CreateInvoiceMutation(invoice)).await()
        mutationResult.data?.invoiceCreate
    }

    fun createMoneyTransaction(invoice: MoneyTransactionCreateInput): CreateTransactionMutation.MoneyTransactionCreate? = runBlocking {
        val mutationResult = client.mutate(CreateTransactionMutation(invoice)).await()
        mutationResult.data?.moneyTransactionCreate
    }
    /* End Invoices */

    /* Start Countries */
    @Cacheable(cacheNames = [WAVE_COUNTRIES])
    fun countries(): Optional<List<CountryListQuery.Country>> = runBlocking {
        val businessResponse = client.query(CountryListQuery())?.await()
        Optional.ofNullable(businessResponse?.data?.countries)
    }
    /* End Countries */

    /* Start Accounts */
    @Cacheable(cacheNames = [WAVE_ACCOUNTS])
    fun businessAccounts(
        businessID: String, page: Int? = 1, pageSize: Int? = 99,
        types: List<AccountTypeValue>? = emptyList(),
        subtypes: List<AccountSubtypeValue>? = emptyList()
    ): List<GetBusinessAccountsQuery.Node>? = runBlocking{
        val businessResponse = client.query(
            GetBusinessAccountsQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
                Input.fromNullable(types), Input.fromNullable(subtypes)
            )
        )?.await()
        businessResponse?.data?.business?.accounts?.edges?.mapNotNull{ e -> e.node}
    }
    /* End Accounts */

    /* Start Customers */
    @Cacheable(cacheNames = [WAVE_CUSTOMERS])
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

    @CacheEvict(WAVE_CUSTOMER, WAVE_CUSTOMERS)
    fun patchCustomer(customer: CustomerPatchInput): Optional<PatchCustomerMutation.CustomerPatch> = runBlocking {
        val mutationResult = client.mutate(PatchCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerPatch)
    }

    @CacheEvict(WAVE_CUSTOMER, WAVE_CUSTOMERS)
    fun createCustomer(customer: CustomerCreateInput): Optional<CreateCustomerMutation.CustomerCreate> = runBlocking {
        val mutationResult = client.mutate(CreateCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerCreate)
    }
    /* End Customer */

    /* Start Products */
    @Cacheable(cacheNames = [WAVE_PRODUCT])
    fun businessProduct(
        businessID: String,
        productID: String
    ): Optional<GetBusinessProductQuery.Product> = runBlocking {
        val businessResponse = client.query(
            GetBusinessProductQuery(
                businessID, productID
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.product)
    }

    fun businessProducts(
        businessID: String,
        page: Int? = 1,
        pageSize: Int? = 99,
    ): Optional<List<GetBusinessProductsQuery.Node>> = runBlocking {
        val businessResponse = client.query(
            GetBusinessProductsQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.products?.edges?.mapNotNull {  it.node })
    }
    /* End Products */

}
