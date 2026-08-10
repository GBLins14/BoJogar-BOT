package com.bojogar.bot.whatsapp.handler

import com.bojogar.bot.whatsapp.client.WhatsAppClient
import com.bojogar.bot.whatsapp.model.IncomingMessage
import com.bojogar.bot.whatsapp.model.WebhookPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MessageHandler(private val whatsAppClient: WhatsAppClient) {

    companion object {
        private val log = LoggerFactory.getLogger(MessageHandler::class.java)
    }

    fun handle(payload: WebhookPayload) {
        log.info("Processing webhook — {} entries", payload.entry.size)

        payload.entry.forEach { entry ->
            log.debug("Entry id: {} — {} changes", entry.id, entry.changes.size)

            entry.changes.forEach { change ->
                log.debug("Change field: {} — messages: {}", change.field, change.value.messages?.size ?: 0)

                if (change.field == "messages") {
                    change.value.messages?.forEach { message ->
                        processMessage(message)
                    }
                }
            }
        }
    }

    private fun processMessage(message: IncomingMessage) {
        log.info("Received [{}] from {}", message.type, message.from)
        log.debug("Message id: {} — text: {}", message.id, message.text?.body)
        start(message.from)
    }

    private fun start(to: String) {
        log.info("Sending /start welcome to {}", to)

        val text = """
            Fala! 🏐 Eu sou o *BoJogar*, seu assistente de peladas!

            Aqui eu te ajudo a organizar tudo pelo WhatsApp:

            ⚽ Criar e gerenciar peladas
            📋 Inscrição de jogadores
            💰 Cobrança e confirmação de Pix
            📊 Lista de jogadores e times
            🔔 Lembretes automáticos

            Como posso te ajudar?
        """.trimIndent()

        whatsAppClient.sendTextMessage(to, text)
    }
}
