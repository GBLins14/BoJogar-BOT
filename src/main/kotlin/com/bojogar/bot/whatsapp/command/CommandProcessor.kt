package com.bojogar.bot.whatsapp.command

import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CommandProcessor(
    private val registry: CommandRegistry,
    private val whatsappService: WhatsAppService
) {

    companion object {
        private val log = LoggerFactory.getLogger(CommandProcessor::class.java)
    }

    fun process(context: CommandContext): Boolean {
        val message = context.rawMessage.trim()
        if (!message.startsWith("/")) return false

        val parts = message.split("\\s+".toRegex())
        val commandName = parts[0]
        val args = parts.drop(1)

        val command = registry.findCommand(commandName)
        if (command == null) {
            log.warn("Command not found: {}", commandName)
            return false
        }

        log.info("Executing command [{}] from {} ({})", commandName, context.senderName, context.from)

        try {
            command.execute(context.copy(args = args), whatsappService)
        } catch (e: Exception) {
            log.error("Error executing command {}: {}", commandName, e.message, e)
            whatsappService.sendMessage(context.from, "Ocorreu um erro. Tente novamente.")
        }

        return true
    }
}
