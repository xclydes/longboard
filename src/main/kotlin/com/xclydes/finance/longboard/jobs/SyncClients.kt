package com.xclydes.finance.longboard.jobs

import com.apollographql.apollo.api.Input
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.component.UpworkToWaveConversion
import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.wave.GetBusinessCustomersQuery
import com.xclydes.finance.longboard.wave.GetBusinessQuery
import com.xclydes.finance.longboard.wave.type.CustomerCreateInput
import com.xclydes.finance.longboard.wave.type.CustomerPatchInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SyncClients(
    val waveSvc: WaveSvc,
    val upworkSvc: UpworkSvc,
    val upworkToWaveConversion: UpworkToWaveConversion
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val jsonReader by lazy { ObjectMapper().reader() }

    fun execute(business: GetBusinessQuery.Business): List<GetBusinessCustomersQuery.Node> {
        // Get the wave customers for the business
        val waveCustomers = waveSvc.businessCustomers(business.id).orElse(emptyList())
        // Get the upwork teams
        val companies = upworkSvc.teams()
        // If the company list if not present
        companies.orElseThrow { IllegalStateException("Invalid company response from Upwork") }
        val nodes = ArrayList<GetBusinessCustomersQuery.Node>(0)
        // If should be an array
        companies.ifPresent { upworkCompanies ->
            // Find the upwork teams which do not have a business
            upworkCompanies
                .mapNotNull { upworkCustomer -> this.convertCustomer(upworkCustomer, waveCustomers) }
                .mapNotNull { toSave ->
                     run {
                        log.info("Saving customer to Wave: ${toSave.name} (${toSave.displayId})")
                        // If the user has an ID
                        if (toSave.id.trim().isNotEmpty()) {
                            // Create a new customer
                            updateCustomer(toSave)
                        } else {
                            // Create a new customer
                            createCustomer(business, toSave)
                        }
                    }
                }
                .forEach{n -> nodes.add(n)}
        }
        return nodes
    }

    private fun convertCustomer(upworkCustomer: JsonNode, waveCustomers: List<GetBusinessCustomersQuery.Node>): GetBusinessCustomersQuery.Node {
        var waveCustomer = waveCustomers
            .find { waveCustomer -> waveCustomer.displayId!!.equals(upworkCustomer["reference"].textValue()) }
        // If there is a customer
        if (waveCustomer != null) {
            // Update the internal notes
            val internalNotesJson = (if (waveCustomer.internalNotes != null
                && waveCustomer.internalNotes!!.isNotEmpty()
            ) {
                jsonReader.readTree(waveCustomer.internalNotes)
            } else jsonReader.createObjectNode()) as ObjectNode
            // Merge the upwork customer into the existing notes
            internalNotesJson.putPOJO("upwork", upworkCustomer)
            // The only thing that should really differ is the internal notes
            val newNotesJsonString = internalNotesJson.toPrettyString()
            // Update the customer
            waveCustomer = waveCustomer.copy(
                waveCustomer.__typename,
                waveCustomer.id,
                waveCustomer.displayId,
                waveCustomer.firstName,
                waveCustomer.lastName,
                waveCustomer.name,
                newNotesJsonString
            )
        } else if (upworkToWaveConversion.canConvert(
                JsonNode::class.java,
                GetBusinessCustomersQuery.Node::class.java
            )
        ) {
            // Use the converted customer
            waveCustomer =
                upworkToWaveConversion.convert(upworkCustomer, GetBusinessCustomersQuery.Node::class.java)
        }
        return waveCustomer!!
    }

    private fun createCustomer(business: GetBusinessQuery.Business,
                               toSave: GetBusinessCustomersQuery.Node): GetBusinessCustomersQuery.Node? {
        // Create the customer
        return waveSvc.createCustomer(
            CustomerCreateInput(
                businessId = business.id,
                name = toSave.name,
                displayId = Input.fromNullable(toSave.displayId),
                firstName = Input.fromNullable(toSave.firstName),
                lastName = Input.fromNullable(toSave.lastName),
                internalNotes = Input.fromNullable(toSave.internalNotes),
            )
        )
        .map { result -> run{
            log.debug("Created customer '${toSave.name}' (${toSave.displayId})? ${result.didSucceed}. Errors: ${result.inputErrors}")
            // If successful
            if (result.didSucceed) {
                val customer = result.customer!!
                GetBusinessCustomersQuery.Node(
                    id = customer.id,
                    name = customer.name,
                    internalNotes = customer.internalNotes,
                    lastName = customer.lastName,
                    firstName = customer.firstName,
                    displayId = customer.displayId,
                )
            } else {
                null
            }
        }}
        .orElse(null)
    }

    private fun updateCustomer(toSave: GetBusinessCustomersQuery.Node): GetBusinessCustomersQuery.Node? {
        // Patch the customer
        return waveSvc.patchCustomer(
            CustomerPatchInput(
                id = toSave.id,
                displayId = Input.fromNullable(toSave.displayId),
                firstName = Input.fromNullable(toSave.firstName),
                lastName = Input.fromNullable(toSave.lastName),
                name = Input.fromNullable(toSave.name),
                internalNotes = Input.fromNullable(toSave.internalNotes),
            )
        )
            .map { result ->
                run {
                    log.debug("Saved customer '${toSave.name}' (${toSave.displayId}) | ${toSave.id})? ${result.didSucceed}. Errors: ${result.inputErrors}")
                    // If successful
                    if (result.didSucceed) {
                        val customer = result.customer!!
                        GetBusinessCustomersQuery.Node(
                            id = customer.id,
                            name = customer.name,
                            internalNotes = customer.internalNotes,
                            lastName = customer.lastName,
                            firstName = customer.firstName,
                            displayId = customer.displayId,
                        )
                    } else {
                        null
                    }
                }
            }
            .orElse(null)
    }
}
