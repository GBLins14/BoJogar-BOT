package com.bojogar.bot.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    companion object {
        private val log = LoggerFactory.getLogger(RestClientConfig::class.java)
    }

    @Bean
    fun whatsAppRestClient(builder: RestClient.Builder, properties: WhatsAppProperties): RestClient {
        log.info("WhatsApp REST client configured — phone-number-id: {}, api-version: {}",
            properties.phoneNumberId, properties.apiVersion)
        return builder
            .baseUrl("https://graph.facebook.com/${properties.apiVersion}")
            .defaultHeader("Authorization", "Bearer ${properties.token}")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }

    @Bean
    fun abacatePayRestClient(builder: RestClient.Builder, properties: AbacatePayProperties): RestClient {
        log.info("AbacatePay REST client configured")
        return builder
            .baseUrl("https://api.abacatepay.com/v2")
            .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
