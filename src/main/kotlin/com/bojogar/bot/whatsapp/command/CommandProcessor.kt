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
            log.warn("Comando não encontrado: \"{}\" de {} ({})", commandName, context.senderName, context.from)
            return false
        }

        log.info("Executando comando [{}] args={} | De: {} ({})", commandName, args, context.senderName, context.from)

        try {
            command.execute(context.copy(args = args), whatsappService)
            log.info("Comando [{}] executado com sucesso para {}", commandName, context.from)
        } catch (e: Exception) {
            log.error("Erro ao executar comando [{}] para {}: {}", commandName, context.from, e.message, e)
            whatsappService.sendMessage(context.from, "Ocorreu um erro. Tente novamente.")
        }

        return true
    }
}
