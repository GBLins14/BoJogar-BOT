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
    fun whatsAppRestClient(properties: WhatsAppProperties): RestClient {
        log.info("WhatsApp REST client configured — phone-number-id: {}, api-version: {}",
            properties.phoneNumberId, properties.apiVersion)
        return RestClient.builder()
            .baseUrl("https://graph.facebook.com/${properties.apiVersion}")
            .defaultHeader("Authorization", "Bearer ${properties.token}")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }

    @Bean
    fun syncPayRestClient(properties: SyncPayProperties): RestClient {
        log.info("SyncPay REST client configured — baseUrl: {}", properties.baseUrl)
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
