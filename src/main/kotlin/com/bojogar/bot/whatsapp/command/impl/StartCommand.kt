package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.SessionManager
import org.springframework.stereotype.Component

@Component
class StartCommand(
    private val sessionManager: SessionManager
) : BotCommand {

    override val name = "/start"
    override val aliases = listOf("/inicio", "/menu")

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        sessionManager.clear(context.from)

        whatsappService.sendButtons(
            to = context.from,
            header = "BoJogar",
            body = buildString {
                append("Fala, ${context.senderName.ifBlank { "jogador" }}! \uD83C\uDFD0\n\n")
                append("Eu sou o *BoJogar*, seu assistente de peladas!\n\n")
                append("O que deseja fazer?")
            },
            buttons = listOf(
                Button(id = "/peladas", title = "Ver Peladas"),
                Button(id = "/minhas", title = "Minhas Peladas"),
                Button(id = "/criar", title = "Criar Pelada")
            ),
            footer = "BoJogar | v2.0"
        )
    }
}
