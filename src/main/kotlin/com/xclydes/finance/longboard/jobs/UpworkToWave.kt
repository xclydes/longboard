package com.xclydes.finance.longboard.jobs

import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.util.DatesUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*

@Component
class UpworkToWave(
    val waveSvc: WaveSvc,
    val  syncClients: SyncClients,
    val  syncTransactions: SyncTransactions,
    @Value("\${longboard.wave.business-id}") val syncBusiness: String,
    @Value("\${longboard.sync.start}") val syncStart: String?,
    @Value("\${longboard.sync.end}") val syncEnd: String?
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val businessOpt by lazy { waveSvc.business(syncBusiness) }

    @Scheduled(initialDelay = 3000, fixedDelay = 3600000)
    fun syncData() {
        // If it wasn't found
        businessOpt.orElseThrow { IllegalStateException("Failed to find business with ID $syncBusiness") }
        // Process the business found
        businessOpt.ifPresent { business ->
            // Use the end date give, or assume today
            var endCalendar = syncEnd?.takeIf { it.isNotBlank() }?.let { toStr ->
                Calendar.getInstance().also { it.time = UpworkSvc.dateFormatSQL.parse( toStr ) }
            } ?: Calendar.getInstance()
            // Start at day 1 of this month
            var startCalendar = syncStart?.takeIf { it.isNotBlank() }?.let { fromStr ->
                Calendar.getInstance().also { it.time = UpworkSvc.dateFormatSQL.parse( fromStr ) }
            } ?:
                // Assume the start of this month
                Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1)  }
            // If the end is after the begining
            if(startCalendar.after(endCalendar)) {
                // Swap them
                val tmp = endCalendar;
                startCalendar = endCalendar;
                endCalendar = tmp;
            }
            log.warn("Processing business '${business.fragments.businessFragment.name}' from ${DatesUtil.dateFormatHuman.format(startCalendar.time)} to ${DatesUtil.dateFormatHuman.format(endCalendar.time)} ")
            // Sync the clients
            val customers = syncClients.execute(business)
            log.debug("Found ${customers.size} customer(s). Names: ${customers.map { customer -> "${customer.fragments.customerFragment.name} (${customer.fragments.customerFragment.displayId})" }}")
            log.trace("Customer List: $customers")
            // Sync invoices
            val invoices = syncTransactions.execute(business, startCalendar.time, endCalendar.time)
            log.debug("Generated ${invoices.size} invoice(s)")
            log.trace("Invoices: $invoices")
        }
    }
}
