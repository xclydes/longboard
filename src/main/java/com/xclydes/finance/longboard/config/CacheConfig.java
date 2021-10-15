package com.xclydes.finance.longboard.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String WAVE_APICLIENT_GRAPHQL = "wave.api_client.graphql";
    public static final String WAVE_APICLIENT_REST = "wave.api_client.rest";
    public static final String WAVE_OAUTH_URL = "wave.oauth.url";
    public static final String WAVE_APIID = "wave.api_id";
    public static final String WAVE_COUNTRIES = "wave.countries";
    public static final String WAVE_BUSINESSES = "wave.businesses";
    public static final String WAVE_BUSINESS = "wave.business";
    public static final String WAVE_INVOICE = "wave.invoice";
    public static final String WAVE_INVOICES = "wave.invoices";
    public static final String WAVE_ACCOUNT = "wave.account";
    public static final String WAVE_ACCOUNTS = "wave.accounts";
    public static final String WAVE_CUSTOMER = "wave.customer";
    public static final String WAVE_CUSTOMERS = "wave.customers";
    public static final String WAVE_PRODUCT = "wave.product";
    public static final String WAVE_PRODUCTS = "wave.products";
    public static final String WAVE_USER = "wave.user";

    public static final String UPWORK_APICLIENT = "upwork.api_client";
    public static final String UPWORK_ACCESSTOKEN = "upwork.token.access";
    public static final String UPWORK_USER = "upwork.user";
    public static final String UPWORK_TEAMS = "upwork.teams";
    public static final String UPWORK_COMPANY = "upwork.company";
    public static final String UPWORK_EARNINGS = "upwork.earnings";
    public static final String UPWORK_ACCOUNTING = "upwork.account.query";
    public static final String UPWORK_ACCOUNTS = "upwork.account.list";


    @Bean
    public CacheManager cacheManager() {
        final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                WAVE_APICLIENT_REST, WAVE_APICLIENT_GRAPHQL, WAVE_OAUTH_URL,
                WAVE_COUNTRIES, WAVE_BUSINESS, WAVE_BUSINESSES, WAVE_INVOICE, WAVE_INVOICES, WAVE_ACCOUNT, WAVE_ACCOUNTS,
                WAVE_CUSTOMER, WAVE_CUSTOMERS, WAVE_PRODUCT, WAVE_PRODUCTS, WAVE_USER, WAVE_APIID,
                UPWORK_APICLIENT, UPWORK_ACCESSTOKEN,
                UPWORK_TEAMS, UPWORK_COMPANY, UPWORK_EARNINGS, UPWORK_USER, UPWORK_ACCOUNTING, UPWORK_ACCOUNTS
        );
        // TODO Use redis if it is configured
        return cacheManager;
    }

}
