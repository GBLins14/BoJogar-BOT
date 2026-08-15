package com.bojogar.bot.whatsapp.handler

import com.bojogar.bot.config.WhatsAppProperties
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
    private val userService: UserService,
    private val whatsAppProperties: WhatsAppProperties
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
                    val incomingPhoneId = change.value.metadata?.phone_number_id
                    if (incomingPhoneId != null && incomingPhoneId != whatsAppProperties.phoneNumberId) {
                        log.info("Mensagem ignorada: phone_number_id {} não corresponde ao bot (esperado {})", incomingPhoneId, whatsAppProperties.phoneNumberId)
                        return@forEach
                    }
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
        log.info("========== NOVA MENSAGEM ==========")
        log.info("Tipo: [{}] | De: {} ({}) | ID: {}", message.type, senderName, message.from, message.id)

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
                log.info("Mensagem recebida (interactive): \"{}\"", replyId)
                replyId
            }
            "button" -> {
                val payload = message.button?.payload
                log.info("Mensagem recebida (button): \"{}\"", payload)
                payload
            }
            "text" -> {
                log.info("Mensagem recebida (text): \"{}\"", message.text?.body)
                message.text?.body
            }
            else -> {
                log.info("Mensagem recebida ({}): tipo não suportado", message.type)
                null
            }
        }

        if (rawMessage.isNullOrBlank()) {
            log.info("Mensagem ignorada: tipo [{}] não suportado de {}", message.type, message.from)
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
        log.info("Roteando mensagem: \"{}\"", rawMessage.trim())
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
            log.info("Comando detectado: \"{}\"", rawMessage)
            // Don't clear session for commands that are part of an active creation/editing flow
            val session = sessionManager.getSession(context.from)
            val isFlowCommand = session != null && session.state != ConversationState.IDLE && (
                rawMessage.startsWith("/criar ") ||
                rawMessage.startsWith("/gerenciar editar_campo ") ||
                rawMessage.startsWith("/pagar cpf_input ")
            )
            if (!isFlowCommand) {
                sessionManager.clear(context.from)
            }
            if (!commandProcessor.process(context)) {
                log.info("No command matched, falling back to /start for {}", context.from)
                commandProcessor.process(context.copy(rawMessage = "/start"))
            }
            return
        }

        // 2. Active session — route to appropriate handler
        val session = sessionManager.getSession(context.from)
        if (session != null && session.state != ConversationState.IDLE) {
            // Check if user wants to cancel the current flow
            if (rawMessage.equals("cancelar", ignoreCase = true)) {
                log.info("Usuário {} cancelou a sessão ativa [{}]", context.from, session.state)
                sessionManager.clear(context.from)
                whatsappService.sendMessage(context.from, "\u274C Ação cancelada.")
                commandProcessor.process(context.copy(rawMessage = "/start"))
                return
            }
            log.info("Sessão ativa [{}] para {} — roteando input: \"{}\"", session.state, context.from, rawMessage)
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
                ConversationState.ADMIN_CONFIG -> {
                    val field = session.currentPeladaCode ?: ""
                    sessionManager.clear(context.from)
                    commandProcessor.process(context.copy(rawMessage = "/adminsuper set$field $rawMessage"))
                }
                else -> {
                    commandProcessor.process(context.copy(rawMessage = "/start"))
                }
            }
            return
        }

        // 3. Check if message looks like a pelada invite code
        if (PELADA_CODE_PATTERN.matches(rawMessage)) {
            log.info("Código de pelada detectado: \"{}\" — redirecionando para /entrar", rawMessage)
            commandProcessor.process(context.copy(rawMessage = "/entrar ${rawMessage.uppercase()}"))
            return
        }

        // 4. Fallback to /start
        log.info("Nenhum comando ou sessão ativa — redirecionando {} para /start", context.from)
        commandProcessor.process(context.copy(rawMessage = "/start"))
    }
}
