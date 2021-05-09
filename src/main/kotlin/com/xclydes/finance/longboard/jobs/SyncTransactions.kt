package com.xclydes.finance.longboard.jobs

import com.apollographql.apollo.api.toInput
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.util.JsonUtil
import com.xclydes.finance.longboard.wave.*
import com.xclydes.finance.longboard.wave.type.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@Component
class SyncTransactions(
    val waveSvc: WaveSvc,
    val upworkSvc: UpworkSvc,
    @Value("#{\${longboard.map.upwork.wave.products}}") val productMappings: Map<String, String>,
    @Value("#{\${longboard.map.upwork.wave.accounts}}") val accountMappings: Map<String, String>
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val dateFormatHuman: DateFormat by lazyOf( SimpleDateFormat("MMM dd, yyyy"))


    internal data class TransactionWrapper(val source: ObjectNode,
                                           val amount: Double,val description: String,
                                           val reference: String, val datePosted: Date, val dateDue: Date,
                                           val type: String, val subType: String,
                                           val typeKey: String = "${type}-${subType}",
                                           var customer: GetBusinessCustomersQuery.Node? = null,
    )

    fun execute(
        business: GetBusinessQuery.Business,
        rptStartDate: Date,
        rptEndDate: Date
    ): List<Any> {
        // Get the wave customers for the business. This should be coming from the cache
        val customers = waveSvc.businessCustomers(business.fragments.businessFragment.id).orElse(emptyList())
        val customersById = customers.associateBy { customer -> customer.fragments.customerFragment.displayId }
        // Fetch and process the list of transactions, for the period, from Upwork
//        val transactionsForPeriod = upworkSvc.earnings(rptStartDate, rptEndDate)
        val transactionsForPeriod = upworkSvc.accountsForEntity(rptStartDate, rptEndDate)
        log.debug("Transactions for $rptStartDate -> $rptEndDate\r\n===\r\n${transactionsForPeriod.toPrettyString()}")
        val resolvedTransactions = transactionsForPeriod
            .map { transaction ->
                // The transaction should be an object node
                val transactionObj = TransactionWrapper(
                    source = transaction as ObjectNode,
                    reference = transaction.required("reference").textValue(),
                    amount = transaction.required("amount").textValue().toDouble(),
                    type = transaction.required("type").textValue(),
                    subType = transaction.required("subtype").textValue(),
                    description = transaction.required("description").textValue(),
                    datePosted = UpworkSvc.dateFormatReport.parse(transaction.required("date").textValue()),
                    dateDue = UpworkSvc.dateFormatReport.parse(transaction.required("date_due").textValue()),
                )
                // Locate the customer, if any
                val customerId = if (transaction.has("buyer_team__reference"))
                    transaction.required("buyer_team__reference").textValue()
                    else ""
                // If there is a valid team reference
                if (!customerId.isNullOrEmpty()) {
                    // Locate the customer
                    transactionObj.customer = customersById[customerId]
                }
                // Save the transaction details
                processTransaction(business, transactionObj)
            }
            .fold(ArrayList<Any?>()) { carry, list -> carry.also { it.addAll(list) } }
            .filterNotNull()
        //log.debug("\r\nCustomer $sharedId\r\n===\r\n\tTransactions: $customerTransactions\r\n\tInvoices\r\n\t---\r\n\t\tExisting: $customerInvoices\r\n\t\tResolved: $resolvedInvoices\r\n===")
        return resolvedTransactions
    }

    private fun processTransaction(
        business: GetBusinessQuery.Business,
        transaction: TransactionWrapper
    ): List<Any?> {
        val generatedEntries = ArrayList<Any?>()
        // If there is a customer
        if(transaction.customer != null) {
            // Process as income
            generateInvoice(transaction, business)?.run {
                generatedEntries.add( this )
            }
        }
        // Process as a bill
        return generatedEntries
    }

    private fun generateInvoice(transaction: TransactionWrapper,
                                business: GetBusinessQuery.Business): GetBusinessInvoiceQuery.Invoice? {
        var invoice: GetBusinessInvoiceQuery.Invoice? = waveSvc.invoices(business.fragments.businessFragment.id, invoiceRef = transaction.reference)
            ?.find { invoice -> invoice.fragments.invoiceFragment.invoiceNumber == transaction.reference }
            ?.let { inv -> waveSvc.invoice(business.fragments.businessFragment.id, inv.fragments.invoiceFragment.id) }
        // If no such invoice exists
        if(invoice == null && transaction.amount > 0) {
            log.debug("Creating invoice for ${transaction.reference}")
            // Determine the product id
            val product = productMappings[transaction.typeKey]?.let{ waveSvc.businessProduct(business.fragments.businessFragment.id, it).orElse( null ) }
            // If the account and product are set
            if( product != null ) {
                // Parse the description
                val descriptionMatcher = UpworkSvc.patternInvoiceDescription.matcher(transaction.description)
                // If it matches
                var quantity = 1.0
                var unitPrice = transaction.amount
                var invoiceTitle = "Week ending ${dateFormatHuman.format(transaction.datePosted)}"
                var description = transaction.description
                if(descriptionMatcher.matches()) {
                    quantity = descriptionMatcher.group(3).toDouble() + (descriptionMatcher.group(4).toInt()/60).toDouble()
                    unitPrice = descriptionMatcher.group(5).toDouble()
                    invoiceTitle = "Week of ${dateFormatHuman.format(UpworkSvc.dateFormatDescription.parse(descriptionMatcher.group(6)))} to ${dateFormatHuman.format(UpworkSvc.dateFormatDescription.parse(descriptionMatcher.group(7)))}"
                    description = "${descriptionMatcher.group(1)} - ${descriptionMatcher.group(2)}"
                }
                // Store the line items
                val items : ArrayList<InvoiceCreateItemInput> = ArrayList()
                // Add the line item
                items.add(
                    InvoiceCreateItemInput(
                        productId = product.fragments.productFragment.id,
                        description = description.toInput(),
                        quantity = quantity.toInput(),
                        unitPrice = unitPrice.toInput()
                    )
                )
                // Generate the metadata
                val metadata = JsonUtil.newObject().also { it.putPOJO("upwork", transaction.source) };
                // Create an invoice
                val invoiceInput = InvoiceCreateInput(
                    business.fragments.businessFragment.id,
                    transaction.customer!!.fragments.customerFragment.id,
                    status = InvoiceCreateStatus.SAVED.toInput(),
                    currency = CurrencyCode.USD.toInput(),
                    invoiceDate = WaveSvc.inputDateFormat.format(transaction.datePosted).toInput(),
                    title = invoiceTitle.toInput(),
                    invoiceNumber = transaction.reference.toInput(),
                    dueDate = WaveSvc.inputDateFormat.format(transaction.dateDue).toInput(),
                    items = items.toInput(),
                    memo = transaction.description.toInput()
                )
                // Submit the create command
                invoice = waveSvc.createInvoice(invoiceInput)?.let { create -> run {
                        log.info("Created invoice ${invoiceInput.title} (${invoiceInput.invoiceNumber})? ${create.didSucceed}. Errors: ${create.inputErrors}")
                        val newInvoice = GetBusinessInvoiceQuery.Invoice(fragments = GetBusinessInvoiceQuery.Invoice.Fragments(invoiceFragment = create.invoice!!.fragments.invoiceFragment))
                        generatePayment(transaction, business, newInvoice)
                        newInvoice
                    }
                }
            } else {
                log.warn("Failed to locate product ${productMappings[transaction.typeKey]} for invoice ${transaction.reference}. Skipping!")
            }
        } else {
            log.debug("Invoice for ${transaction.reference} exists. Skipping!")
        }
        return invoice
    }

    private fun generatePayment(transaction: TransactionWrapper,
                               business: GetBusinessQuery.Business,
                               invoice: GetBusinessInvoiceQuery.Invoice) : String? {
        var payment: String? = null
        // Resolve the account to be updated
        val account = accountMappings[transaction.typeKey]
            ?.let{ acctId -> waveSvc.businessAccounts(business.fragments.businessFragment.id)
            ?.find { acct -> acct.fragments.accountFragment.id == acctId } }
        // If the account was found
        if(account != null) {
            // Submit the payment
            payment = waveSvc.payInvoice(
                invoice,
                account,
                transaction.amount,
                transaction.datePosted,
                "cash",
                JsonUtil.newObject().also { it.putPOJO("upwork", transaction.source) }.toPrettyString(),
            )?.let {
                val paymentId = it.required("id").textValue()
                log.info("Created payment ${paymentId} for invoice ${invoice.fragments.invoiceFragment.invoiceNumber}")
                paymentId
            }
        } else {
            log.warn("Failed to locate account ${accountMappings[transaction.typeKey]} for invoice ${transaction.reference}. Skipping!")
        }
        return payment
    }

    private fun processExpense(transaction: ObjectNode): Unit {
     // Record a negative transaction
    }
}
