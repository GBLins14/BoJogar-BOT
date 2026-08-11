package com.bojogar.bot.whatsapp.handler

import com.bojogar.bot.service.UserService
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.command.CommandProcessor
import com.bojogar.bot.whatsapp.model.IncomingMessage
import com.bojogar.bot.whatsapp.model.WebhookPayload
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.ConversationState
import com.bojogar.bot.whatsapp.session.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MessageHandler(
    private val commandProcessor: CommandProcessor,
    private val whatsappService: WhatsAppService,
    private val sessionManager: SessionManager,
    private val userService: UserService
) {

    companion object {
        private val log = LoggerFactory.getLogger(MessageHandler::class.java)
        private val PELADA_CODE_PATTERN = Regex("^[A-Za-z0-9]{6}$")
        private const val DEDUP_TTL_SECONDS = 300L
    }

    private val processedMessages = ConcurrentHashMap<String, Instant>()

    @Async
    fun handle(payload: WebhookPayload) {
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
                        if (isDuplicate(message.id)) {
                            log.debug("Skipping duplicate message {}", message.id)
                            return@forEach
                        }
                        val senderName = contacts[message.from]?.profile?.name ?: ""
                        processMessage(message, senderName)
                    }
                }
        }
    }

    private fun isDuplicate(messageId: String): Boolean {
        val previous = processedMessages.putIfAbsent(messageId, Instant.now())
        return previous != null
    }

    @Scheduled(fixedRate = 300_000)
    fun cleanupProcessedMessages() {
        val cutoff = Instant.now().minusSeconds(DEDUP_TTL_SECONDS)
        processedMessages.entries.removeIf { it.value.isBefore(cutoff) }
    }

    private fun processMessage(message: IncomingMessage, senderName: String) {
        log.info("Received [{}] from {} ({})", message.type, senderName, message.from)

        // Auto-create user on every interaction
        try {
            userService.findOrCreate(message.from, senderName)
        } catch (e: Exception) {
            log.warn("Failed to auto-create user {}: {}", message.from, e.message)
        }

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
            log.debug("Unsupported message type [{}] from {}, ignoring", message.type, message.from)
            return
        }

        val context = CommandContext(
            from = message.from,
            senderName = senderName,
            messageId = message.id,
            args = emptyList(),
            rawMessage = rawMessage
        )

        // Route the message
        routeMessage(context, rawMessage.trim())

        // Mark as read
        try {
            whatsappService.markAsRead(message.id)
        } catch (e: Exception) {
            log.warn("Failed to mark message as read: {}", e.message)
        }
    }

    private fun routeMessage(context: CommandContext, rawMessage: String) {
        // 1. Explicit command (starts with /)
        if (rawMessage.startsWith("/")) {
            sessionManager.clear(context.from)
            if (!commandProcessor.process(context)) {
                log.info("No command matched, falling back to /start for {}", context.from)
                commandProcessor.process(context.copy(rawMessage = "/start"))
            }
            return
        }

        // 2. Active session — route to appropriate handler
        val session = sessionManager.getSession(context.from)
        if (session != null && session.state != ConversationState.IDLE) {
            when (session.state) {
                ConversationState.CREATING_PELADA -> {
                    val nextField = session.nextField ?: "esporte"
                    commandProcessor.process(context.copy(rawMessage = "/criar input_$nextField $rawMessage"))
                }
                ConversationState.ENTERING_CODE -> {
                    sessionManager.clear(context.from)
                    commandProcessor.process(context.copy(rawMessage = "/entrar ${rawMessage.uppercase()}"))
                }
                ConversationState.EDITING_PELADA -> {
                    val code = session.currentPeladaCode ?: ""
                    val field = session.nextField ?: ""
                    commandProcessor.process(context.copy(rawMessage = "/gerenciar editar_campo $code $field $rawMessage"))
                }
                ConversationState.ENTERING_CPF -> {
                    val code = session.currentPeladaCode ?: ""
                    commandProcessor.process(context.copy(rawMessage = "/pagar cpf_input $code $rawMessage"))
                }
                else -> {
                    commandProcessor.process(context.copy(rawMessage = "/start"))
                }
            }
            return
        }

        // 3. Check if message looks like a pelada invite code
        if (PELADA_CODE_PATTERN.matches(rawMessage)) {
            log.info("Detected possible pelada code: {}", rawMessage)
            commandProcessor.process(context.copy(rawMessage = "/entrar ${rawMessage.uppercase()}"))
            return
        }

        // 4. Fallback to /start
        log.info("No command or session, falling back to /start for {}", context.from)
        commandProcessor.process(context.copy(rawMessage = "/start"))
    }
}
