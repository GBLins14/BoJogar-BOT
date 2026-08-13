package com.bojogar.bot.service

import com.bojogar.bot.config.PushoverProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class PushoverClient(
    private val properties: PushoverProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(PushoverClient::class.java)
        private const val API_URL = "https://api.pushover.net/1/messages.json"
    }

    fun isConfigured(): Boolean = properties.token.isNotBlank() && properties.userKey.isNotBlank()

    fun send(title: String, message: String, priority: Int = 0) {
        if (!isConfigured()) {
            log.debug("Pushover not configured, skipping notification")
            return
        }

        try {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("token", properties.token)
                add("user", properties.userKey)
                add("title", title)
                add("message", message)
                add("priority", priority.toString())
            }

            RestClient.builder().build()
                .post()
                .uri(API_URL)
                .body(form)
                .retrieve()
                .toBodilessEntity()

            log.info("Pushover notification sent: {}", title)
        } catch (e: Exception) {
            log.error("Failed to send Pushover notification: {}", e.message)
        }
    }
}
