package com.bojogar.bot.whatsapp.client

import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.whatsapp.model.MarkAsReadPayload
import com.bojogar.bot.whatsapp.model.MessagePayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@Component
class WhatsAppClient(
    private val whatsAppRestClient: RestClient,
    private val properties: WhatsAppProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(WhatsAppClient::class.java)
    }

    fun sendPayload(payload: MessagePayload) {
        log.info("Sending [{}] to {}", payload.type, payload.to)

        try {
            val response = whatsAppRestClient
                .post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(payload)
                .retrieve()
                .body(Map::class.java)

            log.info("Message sent — response: {}", response)
        } catch (e: HttpClientErrorException.Unauthorized) {
            log.error("Token invalido: {}", e.responseBodyAsString)
        } catch (e: HttpClientErrorException.TooManyRequests) {
            log.error("Rate limit: {}", e.responseBodyAsString)
        } catch (e: HttpClientErrorException) {
            log.error("WhatsApp API error [{}]: {}", e.statusCode, e.responseBodyAsString)
        } catch (e: Exception) {
            log.error(">>> ERRO ao enviar mensagem para {}: {}", payload.to, e.message, e)
        }
    }

    fun markAsRead(messageId: String) {
        log.debug("Marking message {} as read", messageId)

        try {
            whatsAppRestClient
                .post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(MarkAsReadPayload(message_id = messageId))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Failed to mark message as read: {}", e.message)
        }
    }
}
