package com.xclydes.finance.longboard.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig extends CachingConfigurerSupport {

    public static final String WAVE_APICLIENT_GRAPHQL = "wave.api_client.graphql";
    public static final String WAVE_APICLIENT_REST = "wave.api_client.rest";
    public static final String WAVE_ACCESSTOKEN = "wave.token.access";
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
    public static final String UPWORK_USER_BY_TEAM = "upwork.user.team";
    public static final String UPWORK_TEAMS = "upwork.teams";
    public static final String UPWORK_COMPANY = "upwork.company";
    public static final String UPWORK_COMPANY_TEAMS = "upwork.company.teams";
    public static final String UPWORK_COMPANY_WORKDIARY = "upwork.company.diary";
    public static final String UPWORK_EARNINGS_USER = "upwork.earnings.user";
    public static final String UPWORK_EARNINGS_FREELANCER_COMPANY = "upwork.earnings.freelancer.company";
    public static final String UPWORK_EARNINGS_FREELANCER_TEAM = "upwork.earnings.freelancer.team";
    public static final String UPWORK_EARNINGS_BUYER_TEAM = "upwork.earnings.buyer.team";
    public static final String UPWORK_EARNINGS_BUYER_COMPANY = "upwork.earnings.buyer.company";
    public static final String UPWORK_ACCOUNTING_ENTITY = "upwork.accounting.entity";
    public static final String UPWORK_ACCOUNTING_USER = "upwork.accounting.user";
    public static final String UPWORK_BILLING_USER = "upwork.billing.user";
    public static final String UPWORK_TIME_COMPANY = "upwork.time.company";
    public static final String UPWORK_TIME_AGENCY = "upwork.time.agency";
    public static final String UPWORK_TIME_TEAM = "upwork.time.team";


    @Bean
    public CacheManager cacheManager() {
        final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                WAVE_APICLIENT_REST, WAVE_APICLIENT_GRAPHQL, WAVE_OAUTH_URL,
                WAVE_COUNTRIES, WAVE_BUSINESS, WAVE_BUSINESSES, WAVE_INVOICE, WAVE_INVOICES,
                WAVE_ACCOUNT, WAVE_ACCOUNTS, WAVE_ACCESSTOKEN,
                WAVE_CUSTOMER, WAVE_CUSTOMERS, WAVE_PRODUCT, WAVE_PRODUCTS, WAVE_USER, WAVE_APIID,
                UPWORK_APICLIENT, UPWORK_ACCESSTOKEN, UPWORK_TIME_COMPANY, UPWORK_TIME_AGENCY,
                UPWORK_TEAMS, UPWORK_COMPANY, UPWORK_EARNINGS_USER, UPWORK_USER, UPWORK_USER_BY_TEAM,
                UPWORK_EARNINGS_BUYER_TEAM, UPWORK_EARNINGS_BUYER_COMPANY, UPWORK_COMPANY_TEAMS,
                UPWORK_ACCOUNTING_ENTITY, UPWORK_ACCOUNTING_USER, UPWORK_EARNINGS_FREELANCER_COMPANY,
                UPWORK_EARNINGS_FREELANCER_TEAM, UPWORK_COMPANY_WORKDIARY, UPWORK_BILLING_USER,
                UPWORK_TIME_TEAM
        );
        // TODO Use redis if it is configured
        return cacheManager;
    }

}
