package com.xclydes.finance.longboard.svc

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.api.toInput
import com.apollographql.apollo.coroutines.await
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.config.*
import com.xclydes.finance.longboard.util.DatesUtil
import com.xclydes.finance.longboard.util.JsonUtil
import com.xclydes.finance.longboard.wave.*
import com.xclydes.finance.longboard.wave.GetBusinessQuery.Business
import com.xclydes.finance.longboard.wave.GetUserQuery.User
import com.xclydes.finance.longboard.wave.type.*
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Service
import org.springframework.util.Base64Utils
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*

@Service
class WaveSvc(@Autowired @Qualifier("wave-graphql") val clientGraphQL: ApolloClient,
              @Autowired @Qualifier("wave-rest") val clientRest: RestTemplate) {

    companion object {
        const val ID_BUSINESS: String = "Business";
        const val ID_PRODUCT: String = "Product";
        const val ID_ACCOUNT: String = "Account";
        const val ID_INVOICE: String = "Invoice";
        const val ID_TRANSACTION: String = "Transaction";

        val inputDateFormat by lazyOf(DatesUtil.dateFormatSQL)
        val reportDateFormat by lazyOf(DatesUtil.dateFormatReport)
    }

    @Cacheable(cacheNames = [WAVE_APIID])
    fun decodeId(b64Id: String) : Map<String, String> =
        Base64Utils
            .decodeFromString(b64Id)
            .decodeToString()
            .split(delimiters = arrayOf(";", ":"))
            .zipWithNext()
            .toMap()

    fun encodeId(type: String, id: String, business: String? = null) : String {
        var encoded = "${type}:${id}"
        business?.run { encoded += "${ID_BUSINESS}:${business}" }
        return Base64Utils.encodeToString(encoded.toByteArray())
    }

    @Cacheable(cacheNames = [WAVE_USER])
    fun user(): Optional<User> = runBlocking {
        val userResponse = clientGraphQL.query(GetUserQuery()).await()
        return@runBlocking Optional.ofNullable(userResponse.data?.user)
    }

    /* Start Business */
    @Cacheable(cacheNames = [WAVE_BUSINESSES])
    fun businesses(page: Int? = 1, pageSize: Int? = 99): Optional<List<BusinessListQuery.Node>> =
        runBlocking {
            val businessResponse = clientGraphQL.query(
                BusinessListQuery(
                    Input.fromNullable(page), Input.fromNullable(pageSize)
                )
            ).await()
            Optional.ofNullable(businessResponse.data?.businesses?.edges?.mapNotNull { edge -> edge.node })
        }

    @Cacheable(cacheNames = [WAVE_BUSINESS])
    fun business(businessID: String): Optional<Business> = runBlocking {
        val businessResponse = clientGraphQL.query(GetBusinessQuery(businessID))?.await()
        Optional.ofNullable(businessResponse?.data?.business)
    }
    /* Start Business */

    /* Start Invoices */
    @Cacheable(cacheNames = [WAVE_INVOICES])
    fun invoices(
        businessID: String,
        from: LocalDate = LocalDate.EPOCH, to: LocalDate = LocalDate.now(),
        page: Int = 1, pageSize: Int = 99,
        invoiceRef: String? = null
    ) : List<GetBusinessInvoicesQuery.Node>? = runBlocking {
        // No from is set
        val invoicesResponse = clientGraphQL.query(
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
        val invoicesResponse = clientGraphQL.query(
            GetBusinessInvoiceQuery(businessID, invoiceID)
        ).await()
        invoicesResponse.data?.business?.invoice
    }

    @CacheEvict(WAVE_INVOICE, WAVE_INVOICES)
    fun createInvoice(invoice: InvoiceCreateInput): CreateInvoiceMutation.InvoiceCreate? = runBlocking {
        val mutationResult = clientGraphQL.mutate(CreateInvoiceMutation(invoice)).await()
        mutationResult.data?.invoiceCreate
    }

    /**
     * @param method Can be bank_transfer, cash, cheque, credit_card, paypal, other
     */
    fun payInvoice(invoice: GetBusinessInvoiceQuery.Invoice,
                   account: GetBusinessAccountsQuery.Node,
                   payment: Double,
                   paymentDate: LocalDate = LocalDate.now(),
                   method: String = "bank_transfer",
                   memo: String? = null,
                    ): ObjectNode? {
        // Decode the invoice ID
        val decodedInvoiceID = decodeId(invoice.fragments.invoiceFragment.id)
        // Create the account reference
        val acctRef = JsonUtil.newObject()
            .put("id", account.fragments.accountFragment.classicId)
        val url = "/businesses/${decodedInvoiceID[ID_BUSINESS]}/invoices/${decodedInvoiceID[ID_INVOICE]}/payments/"
        var request = RequestEntity
            .post(url)
            .accept(MediaType.APPLICATION_JSON)
            .header("referrer", "https://next.waveapps.com/")
            .contentType(MediaType.APPLICATION_JSON)
            .body(JsonUtil.newObject()
                .put("amount", payment)
                .put("exchange_rate", 1)
                .put("memo", memo)
                .putPOJO("payment_account", acctRef)
                .put("payment_date", inputDateFormat.format(paymentDate))
                .put("payment_method", method)
            )
        val response = clientRest.exchange(request, String::class.java)
        return if(response.statusCodeValue != 201) {
           null
        } else {
            JsonUtil.objectReader.readTree( response.body ) as ObjectNode
        }
    }

    fun createMoneyTransaction(invoice: MoneyTransactionCreateInput): CreateTransactionMutation.MoneyTransactionCreate? = runBlocking {
        val mutationResult = clientGraphQL.mutate(CreateTransactionMutation(invoice)).await()
        mutationResult.data?.moneyTransactionCreate
    }
    /* End Invoices */

    /* Start Bills */

    /* End Bills */

    /* Start Countries */
    @Cacheable(cacheNames = [WAVE_COUNTRIES])
    fun countries(): Optional<List<CountryListQuery.Country>> = runBlocking {
        val businessResponse = clientGraphQL.query(CountryListQuery())?.await()
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
        val businessResponse = clientGraphQL.query(
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
        val businessResponse = clientGraphQL.query(
            GetBusinessCustomersQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
                sort.asList()
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.customers?.edges?.mapNotNull {  it.node })
    }

    @CacheEvict(WAVE_CUSTOMER, WAVE_CUSTOMERS)
    fun patchCustomer(customer: CustomerPatchInput): Optional<PatchCustomerMutation.CustomerPatch> = runBlocking {
        val mutationResult = clientGraphQL.mutate(PatchCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerPatch)
    }

    @CacheEvict(WAVE_CUSTOMER, WAVE_CUSTOMERS)
    fun createCustomer(customer: CustomerCreateInput): Optional<CreateCustomerMutation.CustomerCreate> = runBlocking {
        val mutationResult = clientGraphQL.mutate(CreateCustomerMutation(customer)).await()
        Optional.ofNullable(mutationResult.data?.customerCreate)
    }
    /* End Customer */

    /* Start Products */
    @Cacheable(cacheNames = [WAVE_PRODUCT])
    fun businessProduct(
        businessID: String,
        productID: String
    ): Optional<GetBusinessProductQuery.Product> = runBlocking {
        val businessResponse = clientGraphQL.query(
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
        val businessResponse = clientGraphQL.query(
            GetBusinessProductsQuery(
                businessID, Input.fromNullable(page), Input.fromNullable(pageSize),
            )
        )?.await()
        Optional.ofNullable(businessResponse?.data?.business?.products?.edges?.map {  it.node })
    }
    /* End Products */

}
