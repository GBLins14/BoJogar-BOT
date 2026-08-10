package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component

@Component
class StartCommand : BotCommand {

    override val name = "/start"
    override val aliases = listOf("/inicio", "/menu")

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        whatsappService.sendButtons(
            to = context.from,
            header = "BoJogar",
            body = buildString {
                append("Fala! \uD83C\uDFD0 Eu sou o *BoJogar*, seu assistente de peladas!\n\n")
                append("O que deseja fazer?")
            },
            buttons = listOf(
                Button(id = "/peladas", title = "Ver Peladas"),
                Button(id = "/minhas", title = "Minhas Peladas"),
                Button(id = "/conta", title = "Minha Conta")
            ),
            footer = "BoJogar | v1.0"
        )
    }
}
