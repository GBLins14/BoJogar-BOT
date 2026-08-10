package com.bojogar.bot.whatsapp.client

import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.whatsapp.model.SendMessageRequest
import com.bojogar.bot.whatsapp.model.TextContent
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

    fun sendTextMessage(to: String, text: String) {
        log.info("Sending [text] to {}", to)

        val request = SendMessageRequest(
            to = to,
            text = TextContent(body = text)
        )

        try {
            val response = whatsAppRestClient
                .post()
                .uri("/${properties.phoneNumberId}/messages")
                .body(request)
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
            log.error(">>> ERRO ao enviar mensagem para {}: {}", to, e.message, e)
        }
    }
}
