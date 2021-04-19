package com.xclydes.finance.longboard.config

import com.Upwork.api.Config
import com.Upwork.api.OAuthClient
import com.apollographql.apollo.ApolloClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class ClientConfig {

    @Bean
    fun wave(@Value("\${longboard.wave.endpoint}") endpointUrl: String,
             @Value("\${longboard.wave.client}") clientId: String,
             @Value("\${longboard.wave.secret}") clientSecret: String,
             @Value("\${longboard.wave.token}") token: String): ApolloClient
    {
        val apolloBuilder = ApolloClient.builder().serverUrl(endpointUrl)
        val okHttpBuilder = OkHttpClient.Builder()
        // Add the credentials
        val authInterceptor = Interceptor { chain: Interceptor.Chain ->
            chain.proceed(chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build())
        }
        okHttpBuilder.addInterceptor(authInterceptor)
        // Set the HTTP client
        apolloBuilder.okHttpClient(okHttpBuilder.build())
        return apolloBuilder.build()
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
