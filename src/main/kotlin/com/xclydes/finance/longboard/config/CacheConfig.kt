package com.xclydes.finance.longboard.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val WAVE_COUNTRIES: String = "wave.countries"
const val WAVE_BUSINESSES: String = "wave.businesses"
const val WAVE_BUSINESS: String = "wave.business"
const val WAVE_ACCOUNT: String = "wave.accounts"
const val WAVE_CUSTOMER: String = "wave.customers"
const val WAVE_USER: String = "wave.user"
const val UPWORK_USER: String = "upwork.user"
const val UPWORK_TEAMS: String = "upwork.teams"
const val UPWORK_COMPANY: String = "upwork.company"
const val UPWORK_EARNINGS: String = "upwork.earnings"

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager? {
        // TODO Use redis if it is configured
        val cacheManager = ConcurrentMapCacheManager(WAVE_COUNTRIES, WAVE_BUSINESS, WAVE_ACCOUNT, WAVE_CUSTOMER,
            WAVE_USER, UPWORK_TEAMS, UPWORK_COMPANY, WAVE_BUSINESSES, UPWORK_EARNINGS, UPWORK_USER)
        return cacheManager
    }
}
