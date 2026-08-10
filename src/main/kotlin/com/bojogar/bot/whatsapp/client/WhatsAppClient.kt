package com.bojogar.bot.whatsapp.client

import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.whatsapp.model.SendMessageRequest
import com.bojogar.bot.whatsapp.model.TextContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class WhatsAppClient(
    private val whatsAppRestClient: RestClient,
    private val properties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendTextMessage(to: String, text: String) {
        val request = SendMessageRequest(
            to = to,
            text = TextContent(body = text)
        )

        try {
            whatsAppRestClient
                .post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(request)
                .retrieve()
                .toBodilessEntity()
            log.info("Mensagem enviada para {}", to)
        } catch (ex: Exception) {
            log.error("Erro ao enviar mensagem para {}: {}", to, ex.message)
        }
    }
}
