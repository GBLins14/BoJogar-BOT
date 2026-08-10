package com.bojogar.bot.whatsapp.command

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CommandRegistry(commands: List<BotCommand>) {

    companion object {
        private val log = LoggerFactory.getLogger(CommandRegistry::class.java)
    }

    private val commandMap: Map<String, BotCommand>

    init {
        val map = mutableMapOf<String, BotCommand>()
        commands.forEach { cmd ->
            map[cmd.name.lowercase()] = cmd
            cmd.aliases.forEach { alias ->
                map[alias.lowercase()] = cmd
            }
        }
        commandMap = map
        log.info("Registered {} commands: {}", commands.size, commands.map { it.name })
    }

    fun findCommand(name: String): BotCommand? =
        commandMap[name.lowercase()]
}
