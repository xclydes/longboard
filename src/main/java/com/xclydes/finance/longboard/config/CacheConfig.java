package com.xclydes.finance.longboard.config;

import com.google.common.cache.CacheBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig extends CachingConfigurerSupport {

    public static final long defaultTTL = 3600;

    @Bean
    public Map<String, Long> cacheTTLs() {
        // TODO Populate this using the config
        return new TreeMap<>();
    }

    @Bean
    public CacheManager cacheManager(final Map<String, Long> cacheTTLs) {
        final List<String> allCacheKeys = new ArrayList<>();
        // Gather the keys using reflection
        ReflectionUtils.doWithFields(
            CacheKeys.class,
            field -> {
                final String keyValue = (String) field.get(null);
                allCacheKeys.add(keyValue);
            },
            field -> StringUtils.startsWithIgnoreCase(field.getName(), "WAVE_") ||
            StringUtils.startsWithIgnoreCase(field.getName(), "UPWORK_")
        );
        // TODO Use redis if it is configured
        return new ConcurrentMapCacheManager(allCacheKeys.toArray(new String[0])) {
            @NotNull
            @Override
            protected Cache createConcurrentMapCache(@NotNull final String name) {
                // Get the TTL for the specific cache
                final long ttl = cacheTTLs.getOrDefault(name, defaultTTL);
                final ConcurrentMap<Object, Object> objectObjectConcurrentMap = CacheBuilder.newBuilder()
                        .expireAfterWrite(ttl, TimeUnit.SECONDS)
                        .maximumSize(100)
                        .build()
                        .asMap();
                return new ConcurrentMapCache(name, objectObjectConcurrentMap, false);
            }
        };
    }

    public interface CacheKeys {
        String WAVE_APICLIENT_GRAPHQL = "wave.api_client.graphql";
        String WAVE_APICLIENT_REST = "wave.api_client.rest";
        String WAVE_ACCESSTOKEN = "wave.token.access";
        String WAVE_OAUTH_URL = "wave.oauth.url";
        String WAVE_APIID = "wave.api_id";
        String WAVE_COUNTRIES = "wave.countries";
        String WAVE_BUSINESSES = "wave.businesses";
        String WAVE_BUSINESS = "wave.business";
        String WAVE_INVOICE = "wave.invoice";
        String WAVE_INVOICES = "wave.invoices";
        String WAVE_INVOICES_PAGE = "wave.invoices_page";
        String WAVE_ACCOUNT = "wave.account";
        String WAVE_ACCOUNTS = "wave.accounts";
        String WAVE_CUSTOMER = "wave.customer";
        String WAVE_CUSTOMERS = "wave.customers";
        String WAVE_PRODUCT = "wave.product";
        String WAVE_PRODUCTS = "wave.products";
        String WAVE_USER = "wave.user";
        String UPWORK_APICLIENT = "upwork.api_client";
        String UPWORK_ACCESSTOKEN = "upwork.token.access";
        String UPWORK_USER = "upwork.user";
        String UPWORK_PROFILE = "upwork.profile";
        String UPWORK_USER_BY_TEAM = "upwork.user.team";
        String UPWORK_TEAMS = "upwork.teams";
        String UPWORK_ENGAGEMENTS = "upwork.engagements";
        String UPWORK_COMPANY = "upwork.company";
        String UPWORK_COMPANY_TEAMS = "upwork.company.teams";
        String UPWORK_COMPANY_WORKDIARY = "upwork.company.diary";
        String UPWORK_EARNINGS_USER = "upwork.earnings.user";
        String UPWORK_EARNINGS_FREELANCER_COMPANY = "upwork.earnings.freelancer.company";
        String UPWORK_EARNINGS_FREELANCER_TEAM = "upwork.earnings.freelancer.team";
        String UPWORK_EARNINGS_BUYER_TEAM = "upwork.earnings.buyer.team";
        String UPWORK_EARNINGS_BUYER_COMPANY = "upwork.earnings.buyer.company";
        String UPWORK_ACCOUNTING_ENTITY = "upwork.accounting.entity";
        String UPWORK_ACCOUNTING_USER = "upwork.accounting.user";
        String UPWORK_BILLING_USER = "upwork.billing.user";
        String UPWORK_BILLINGS_FREELANCER_COMPANY = "upwork.billing.freelancer.company";
        String UPWORK_BILLINGS_BUYER_TEAM = "upwork.billing.buyer.team";
        String UPWORK_BILLINGS_BUYER_COMPANY = "upwork.billing.buyer.company";
        String UPWORK_TIME_COMPANY = "upwork.time.company";
        String UPWORK_TIME_AGENCY = "upwork.time.agency";
        String UPWORK_TIME_TEAM = "upwork.time.team";
        String UPWORK_TIME_USER = "upwork.time.user";
    }
}
