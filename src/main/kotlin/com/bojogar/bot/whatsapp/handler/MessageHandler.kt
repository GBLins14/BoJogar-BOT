package com.bojogar.bot.whatsapp.handler

import com.bojogar.bot.whatsapp.client.WhatsAppClient
import com.bojogar.bot.whatsapp.model.IncomingMessage
import com.bojogar.bot.whatsapp.model.WebhookPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MessageHandler(private val whatsAppClient: WhatsAppClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(payload: WebhookPayload) {
        payload.entry.forEach { entry ->
            entry.changes
                .filter { it.field == "messages" }
                .forEach { change ->
                    change.value?.messages?.forEach { message ->
                        processMessage(message)
                    }
                }
        }
    }

    private fun processMessage(message: IncomingMessage) {
        log.info("Mensagem recebida de {}: {}", message.from, message.text?.body)
        start(message.from)
    }

    private fun start(to: String) {
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
