package com.bojogar.bot.whatsapp.handler

import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.command.CommandProcessor
import com.bojogar.bot.whatsapp.model.IncomingMessage
import com.bojogar.bot.whatsapp.model.WebhookPayload
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MessageHandler(
    private val commandProcessor: CommandProcessor,
    private val whatsappService: WhatsAppService
) {

    companion object {
        private val log = LoggerFactory.getLogger(MessageHandler::class.java)
    }

    fun handle(payload: WebhookPayload) {
        log.info("Processing webhook — {} entries", payload.entry.size)

        val contacts = payload.entry
            .flatMap { it.changes }
            .filter { it.field == "messages" }
            .flatMap { it.value.contacts ?: emptyList() }
            .associateBy { it.wa_id }

        payload.entry.forEach { entry ->
            entry.changes
                .filter { it.field == "messages" }
                .forEach { change ->
                    change.value.messages?.forEach { message ->
                        val senderName = contacts[message.from]?.profile?.name ?: ""
                        processMessage(message, senderName)
                    }
                }
        }
    }

    private fun processMessage(message: IncomingMessage, senderName: String) {
        log.info("Received [{}] from {} ({})", message.type, senderName, message.from)

        // Extract raw message from text or interactive reply
        val rawMessage = when (message.type) {
            "interactive" -> {
                val replyId = message.interactive?.button_reply?.id
                    ?: message.interactive?.list_reply?.id
                log.info("Interactive reply: {}", replyId)
                replyId
            }
            "button" -> {
                val payload = message.button?.payload
                log.info("Button reply: {}", payload)
                payload
            }
            "text" -> message.text?.body
            else -> null
        }

        if (rawMessage.isNullOrBlank()) {
            log.warn("Empty message from {}, ignoring", message.from)
            return
        }

        val context = CommandContext(
            from = message.from,
            senderName = senderName,
            messageId = message.id,
            args = emptyList(),
            rawMessage = rawMessage
        )

        // Try to process as command, fallback to /start
        if (!commandProcessor.process(context)) {
            log.info("No command matched, falling back to /start for {}", message.from)
            commandProcessor.process(context.copy(rawMessage = "/start"))
        }

        // Mark as read
        try {
            whatsappService.markAsRead(message.id)
        } catch (e: Exception) {
            log.warn("Failed to mark message as read: {}", e.message)
        }
    }
}
