package com.bojogar.bot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    @Bean
    fun whatsAppRestClient(properties: WhatsAppProperties): RestClient =
        RestClient.builder()
            .baseUrl("https://graph.facebook.com/v21.0")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.token}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
}
