package com.xclydes.finance.longboard.jobs

import com.xclydes.finance.longboard.svc.WaveSvc
import com.xclydes.finance.longboard.util.DatesUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.streams.toList


@Component
class UpworkToWave(
    val waveSvc: WaveSvc,
    val  syncClients: SyncClients,
    val  syncTransactions: SyncTransactions,
    @Value("\${longboard.wave.business-id}") val syncBusiness: String,
    @Value("\${longboard.sync.span}") val syncSpan: String,
    @Value("\${longboard.sync.start}") val syncStart: String?,
    @Value("\${longboard.sync.end}") val syncEnd: String?,
) {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))
    private val businessOpt by lazy { waveSvc.business(syncBusiness) }

    @Scheduled(initialDelay = 3000, fixedDelay = 3600000)
    fun syncData() {
        // If it wasn't found
        businessOpt.orElseThrow { IllegalStateException("Failed to find business with ID $syncBusiness") }
        // Process the business found
        businessOpt.ifPresent { business ->
            // Resolve the set of dates to be processed
            val datePairs = generateDatePairs()
            log.warn("Processing business '${business.fragments.businessFragment.name}' from ${DatesUtil.dateFormatHuman.format(datePairs.first().first)} to ${DatesUtil.dateFormatHuman.format(datePairs.last().second)} ")
            // Sync the clients
            val customers = syncClients.execute(business)
            log.debug("Found ${customers.size} customer(s). Names: ${customers.map { customer -> customer.fragments.customerFragment.let { "${it.name} (${it.displayId})" } }}")
            log.trace("Customer List: $customers")
            // Sync invoices
            datePairs.forEach { datePair ->
                log.trace("Syncing Transactions between ${datePair.first.format(DatesUtil.dateFormatHuman)} and ${datePair.second.format(DatesUtil.dateFormatHuman)} ")
                val invoices = syncTransactions.execute(business, datePair.first, datePair.second)
                log.debug("Generated ${invoices.size} invoice(s)")
                log.trace("Invoices: $invoices")
            }
        }
    }

    private fun generateDatePairs() : List<Pair<LocalDate, LocalDate>>{
        // Use the end date give, or assume today
        var resolvedEnd = syncEnd?.takeIf { it.isNotBlank() }?.let {
            DatesUtil.dateFormatSQL.parse(it, LocalDate::from)
        } ?: LocalDate.now()
        // Start at day 1 of this month, or assume the first date of this month
        var resolvedStart = syncStart?.takeIf { it.isNotBlank() }?.let {
            DatesUtil.dateFormatSQL.parse(it, LocalDate::from)
        } ?: LocalDate.now().withDayOfMonth(1)
        // If the end is after the begining
        if(resolvedStart.isAfter(resolvedEnd)) {
            // Swap them
            val tmp = resolvedStart;
            resolvedStart = resolvedEnd
            resolvedEnd = tmp;
        }
        log.trace("Splitting dates between ${resolvedStart.format(DatesUtil.dateFormatHuman)} and ${resolvedEnd.format(DatesUtil.dateFormatHuman)}")
        return resolvedStart.datesUntil(resolvedEnd, Period.ofMonths(1))
            .toList()
            .plus(resolvedEnd)
            .zipWithNext()
    }
}
