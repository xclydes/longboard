package com.xclydes.finance.longboard.jobs

import com.apollographql.apollo.api.Input
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.xclydes.finance.longboard.component.UpworkToWaveConversion
import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.wave.CreateCustomerMutation
import com.xclydes.finance.longboard.wave.GetBusinessCustomersQuery
import com.xclydes.finance.longboard.wave.GetBusinessQuery
import com.xclydes.finance.longboard.wave.type.CustomerCreateInput
import com.xclydes.finance.longboard.wave.type.CustomerPatchInput
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UpworkToWave(
    @Autowired val waveSvc: WaveSvc,
    @Autowired val upworkSvc: UpworkSvc,
    @Autowired val upworkToWaveConversion: UpworkToWaveConversion,
    @Value("\${longboard.wave.business-id}") val syncBusiness: String
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val objectMapper by lazyOf(ObjectMapper())

    @Scheduled(initialDelay = 3000, fixedDelay = 3600000)
    fun syncData() {
        // Fetch the business specified
        val businessOpt = waveSvc.business(syncBusiness)
        // If it wasnt found
        businessOpt.orElseThrow { IllegalStateException("Failed to find business with ID $syncBusiness") }
        // Process the business found
        businessOpt.ifPresent { business ->
            log.warn("Processing business ${business.name}")
            // Sync the clients
            syncClients(business)
            // Sync invoices
        }
    }

    /**
     * Fetches list of clients from Upwork and creates them in Wave
     */
    private fun syncClients(business: GetBusinessQuery.Business) {
        val jsonReader = objectMapper.reader()
        // Get the wave customers for the business
        val waveCustomers = waveSvc.businessCustomers(business.id).orElse(emptyList())
        // Get the upwork teams
        val companies = upworkSvc.teams()
        // If the company list if not present
        companies.orElseThrow { IllegalStateException("Invalid company response from Upwork") }
        // If should be an array
        companies.ifPresent { upworkCompanies ->
            // Find the upwork teams which do not have a business
            val upworkCustomers = upworkCompanies
                .mapNotNull { upworkCustomer ->
                    var waveCustomer = waveCustomers
                        .find { waveCustomer -> waveCustomer.displayId!!.equals( upworkCustomer["reference"].textValue() ) }
                    // If there is a customer
                    if (waveCustomer != null) {
                        // Update the internal notes
                        val internalNotesJson = (if(waveCustomer.internalNotes != null
                            && waveCustomer.internalNotes!!.isNotEmpty()) {
                            jsonReader.readTree(waveCustomer.internalNotes)
                            } else jsonReader.createObjectNode()) as ObjectNode
                        // Merge the upwork customer into the existing notes
                        internalNotesJson.putPOJO("upwork", upworkCustomer)
                        // Update the customer
                        waveCustomer = waveCustomer.copy(
                            waveCustomer.__typename,
                            waveCustomer.id,
                            waveCustomer.displayId,
                            waveCustomer.firstName,
                            waveCustomer.lastName,
                            waveCustomer.name,
                            internalNotesJson.toPrettyString()
                        )
                    } else if ( upworkToWaveConversion.canConvert(JsonNode::class.java, GetBusinessCustomersQuery.Node::class.java)){
                        // Use the converted customer
                        waveCustomer = upworkToWaveConversion.convert(upworkCustomer, GetBusinessCustomersQuery.Node::class.java)
                    }
                    waveCustomer
                }
                .forEach { toSave -> run {
                    log.info("Saving customer to Wave  ${toSave.name} (${toSave.displayId})")
                    // If the user has an ID
                    if(toSave.id.trim().isNotEmpty()) {
                        // Patch the customer
                        waveSvc.patchCustomer(CustomerPatchInput(
                            id = toSave.id,
                            displayId = Input.fromNullable(toSave.displayId),
                            firstName = Input.fromNullable(toSave.firstName),
                            lastName = Input.fromNullable(toSave.lastName),
                            name = Input.fromNullable(toSave.name),
                            internalNotes = Input.fromNullable(toSave.internalNotes),
                        ))
                        .map { result -> log.debug("Saved customer '${toSave.name}' (${toSave.displayId} | ${toSave.id})? ${result.didSucceed}. Errors: ${result.inputErrors}") }
                    } else {
                        // Create a new customer
                        log.warn("Creating customer with 'Account Number': ${toSave.displayId}, Name: ${toSave.name}")
                        waveSvc.createCustomer(CustomerCreateInput(
                            businessId = business.id,
                            name = toSave.name,
                            displayId = Input.fromNullable(toSave.displayId),
                            firstName = Input.fromNullable(toSave.firstName),
                            lastName = Input.fromNullable(toSave.lastName),
                            internalNotes = Input.fromNullable(toSave.internalNotes),
                        ))
                        .map { result -> log.debug("Created customer '${toSave.name}' (${toSave.displayId})? ${result.didSucceed}. Errors: ${result.inputErrors}") }
                    }
                } }
        }
    }
}
