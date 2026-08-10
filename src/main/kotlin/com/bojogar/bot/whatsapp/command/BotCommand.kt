package com.bojogar.bot.whatsapp.command

import com.bojogar.bot.whatsapp.service.WhatsAppService

interface BotCommand {
    val name: String
    val aliases: List<String> get() = emptyList()

    fun execute(context: CommandContext, whatsappService: WhatsAppService)
}
