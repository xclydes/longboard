package com.xclydes.finance.longboard.jobs

import com.xclydes.finance.longboard.svc.UpworkSvc
import com.xclydes.finance.longboard.svc.WaveSvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Invoices(@Autowired val waveSvc: WaveSvc,
                @Autowired val upworkSvc: UpworkSvc) {

//    @Scheduled()
    fun syncInvoices() {
        // Sync the clients
        // Sync invoices
    }

    /**
     * Fetches list of clients from Upwork and creates them in Wave
     */
    private fun syncClients() {
    }
}
