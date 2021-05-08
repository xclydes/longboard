package com.xclydes.finance.longboard.jobs

import com.fasterxml.jackson.databind.ObjectMapper
import com.xclydes.finance.longboard.component.UpworkToWaveConversion
import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.wave.GetBusinessCustomersQuery
import com.xclydes.finance.longboard.wave.GetBusinessQuery
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val businessOpt by lazy { waveSvc.business(syncBusiness) }

    @Scheduled(initialDelay = 3000, fixedDelay = 3600000)
    fun syncData() {
        // If it wasn't found
        businessOpt.orElseThrow { IllegalStateException("Failed to find business with ID $syncBusiness") }
        // Process the business found
        businessOpt.ifPresent { business ->
            log.warn("Processing business ${business.name}")
            // Sync the clients
            val customers = syncClients.execute(business)
            log.debug("Customer List: $customers")
            // Start by processing the current month
            val endCalendar = Calendar.getInstance()
            val startCalendar = Calendar.getInstance()
            startCalendar.set(Calendar.DAY_OF_MONTH, 1)// Always the start of the month
            // Sync invoices
            val invoices = syncTransactions.execute(business, startCalendar.time, endCalendar.time)
            log.debug("Invoices: $invoices")
        }
    }
}
