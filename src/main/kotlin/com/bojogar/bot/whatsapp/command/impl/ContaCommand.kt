package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component

@Component
class ContaCommand : BotCommand {

    override val name = "/conta"
    override val aliases = listOf("/perfil")

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showConta(context, whatsappService)
            "peladas" -> showPeladas(context, whatsappService)
            "resetar" -> showResetar(context, whatsappService)
            "resetar_confirmar" -> confirmarReset(context, whatsappService)
            else -> showConta(context, whatsappService)
        }
    }

    private fun showConta(context: CommandContext, ws: WhatsAppService) {
        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDC64 *Minha Conta*\n\n")
                append("\uD83D\uDCDD *Nome:* ${context.senderName}\n")
                append("\uD83D\uDCF1 *Telefone:* ${context.from}\n")
                append("\uD83C\uDFD0 *Peladas ativas:* 2\n")
                append("\uD83D\uDCCA *Total participadas:* 7")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/conta peladas", title = "Minhas Peladas"),
                Button(id = "/conta resetar", title = "Resetar Conta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showPeladas(context: CommandContext, ws: WhatsAppService) {
        ws.sendList(
            to = context.from,
            header = "Minhas Peladas",
            body = "\uD83C\uDFD0 Peladas que voce esta participando:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Ativas",
                    rows = listOf(
                        ListRow(
                            id = "/minhas ver PEL01",
                            title = "Futevolei - Boa Viagem",
                            description = "Ter 12/08 - 19h | Confirmado"
                        ),
                        ListRow(
                            id = "/minhas ver PEL03",
                            title = "Futevolei - Candeias",
                            description = "Seg 18/08 - 17h | Pendente"
                        )
                    )
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun showResetar(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "Resetar Conta",
            body = buildString {
                append("\u26A0\uFE0F *Atencao!* Esta acao ira:\n\n")
                append("\u274C Cancelar todas as suas inscricoes\n")
                append("\u274C Sair de todas as peladas\n")
                append("\u274C Zerar seu historico\n\n")
                append("*Essa acao nao pode ser desfeita.*")
            },
            buttons = listOf(
                Button(id = "/conta resetar_confirmar", title = "Sim, Resetar"),
                Button(id = "/conta", title = "Cancelar")
            )
        )
    }

    private fun confirmarReset(context: CommandContext, ws: WhatsAppService) {
        ws.sendMessage(
            context.from,
            buildString {
                append("\u2705 *Conta Resetada*\n\n")
                append("Todas as suas inscricoes foram canceladas e seu historico foi zerado.\n\n")
                append("Voce pode comecar de novo a qualquer momento!")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/peladas", title = "Ver Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
