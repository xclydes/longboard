package com.xclydes.finance.longboard.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val WAVE_APIID: String = "wave.api_id"
const val WAVE_COUNTRIES: String = "wave.countries"
const val WAVE_BUSINESSES: String = "wave.businesses"
const val WAVE_BUSINESS: String = "wave.business"
const val WAVE_INVOICE: String = "wave.invoice"
const val WAVE_INVOICES: String = "wave.invoices"
const val WAVE_ACCOUNT: String = "wave.account"
const val WAVE_ACCOUNTS: String = "wave.accounts"
const val WAVE_CUSTOMER: String = "wave.customer"
const val WAVE_CUSTOMERS: String = "wave.customers"
const val WAVE_PRODUCT: String = "wave.product"
const val WAVE_PRODUCTS: String = "wave.products"
const val WAVE_USER: String = "wave.user"

const val UPWORK_USER: String = "upwork.user"
const val UPWORK_TEAMS: String = "upwork.teams"
const val UPWORK_COMPANY: String = "upwork.company"
const val UPWORK_EARNINGS: String = "upwork.earnings"
const val UPWORK_ACCOUNTING: String = "upwork.account.query"
const val UPWORK_ACCOUNTS: String = "upwork.account.list"

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager? {
        // TODO Use redis if it is configured
        val cacheManager = ConcurrentMapCacheManager(
            WAVE_COUNTRIES, WAVE_BUSINESS, WAVE_BUSINESSES, WAVE_INVOICE, WAVE_INVOICES, WAVE_ACCOUNT, WAVE_ACCOUNTS,
            WAVE_CUSTOMER, WAVE_CUSTOMERS, WAVE_PRODUCT, WAVE_PRODUCTS, WAVE_USER, WAVE_APIID,
            UPWORK_TEAMS, UPWORK_COMPANY, UPWORK_EARNINGS, UPWORK_USER, UPWORK_ACCOUNTING, UPWORK_ACCOUNTS
        )
        return cacheManager
    }
}
