package com.xclydes.finance.longboard.config

import com.Upwork.api.Config
import com.Upwork.api.OAuthClient
import com.apollographql.apollo.ApolloClient
import com.xclydes.finance.longboard.component.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.util.*


@Configuration
class ClientConfig {

    @Bean
    @Qualifier("wave-rest")
    fun waveRest(@Value("\${longboard.wave.endpoint.rest}") endpointUrl: String,
                    @Value("\${longboard.wave.client.debug}") debug: Boolean,
                    @Value("\${longboard.wave.token}") token: String): RestTemplate =
        RestTemplateBuilder()
            .defaultHeader("Authorization", "Bearer ${token}")
            .rootUri(endpointUrl)
            .build()

    @Bean
    @Qualifier("wave-graphql")
    fun waveGraphQL(@Value("\${longboard.wave.endpoint.graghql}") endpointUrl: String,
                    @Value("\${longboard.wave.client.key}") clientId: String,
                    @Value("\${longboard.wave.client.secret}") clientSecret: String,
                    @Value("\${longboard.wave.client.debug}") debug: Boolean,
                    @Value("\${longboard.wave.token}") token: String): ApolloClient
    {
        // Add the credentials
        val authInterceptor = Interceptor { chain: Interceptor.Chain ->
            chain.proceed(chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build())
        }
        val httpInterceptor = HttpLoggingInterceptor()
        return ApolloClient.builder().serverUrl(endpointUrl)
        .also { apolloBldr ->
            val okHttpBuilder = with(OkHttpClient.Builder()) {
                this.addInterceptor(authInterceptor)
                if(debug) {
                    this.addNetworkInterceptor(httpInterceptor)
                }
                this
            }
            // Set the HTTP client
            apolloBldr.okHttpClient(okHttpBuilder.build())
        }
        // Return the final result
        .build()
    }

    @Bean
    fun upwork(@Value("\${longboard.upwork.client.key}") clientKey: String,
               @Value("\${longboard.upwork.client.secret}") clientSecret: String,
               @Value("\${longboard.upwork.token.value}") tokenValue: String,
               @Value("\${longboard.upwork.token.secret}") tokenSecret: String): OAuthClient {
        val keys = Properties()
        keys.setProperty("consumerKey", clientKey)
        keys.setProperty("consumerSecret", clientSecret)
        // Build the config
        val config = Config(keys)
        val client = OAuthClient(config)
        // Update the client
        client.setTokenWithSecret(tokenValue, tokenSecret)
        // Return this configured client
        return client
    }
}
