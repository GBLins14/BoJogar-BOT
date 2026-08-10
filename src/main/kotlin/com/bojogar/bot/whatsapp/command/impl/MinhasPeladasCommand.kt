package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component

@Component
class MinhasPeladasCommand : BotCommand {

    override val name = "/minhas"

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showMenu(context, whatsappService)
            "proximas" -> showProximas(context, whatsappService)
            "ver" -> showDetalhes(context, whatsappService)
            "cancelar" -> showCancelar(context, whatsappService)
            "cancelar_sim" -> confirmarCancelamento(context, whatsappService)
            "historico" -> showHistorico(context, whatsappService)
            else -> showMenu(context, whatsappService)
        }
    }

    private fun showMenu(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "Minhas Peladas",
            body = "\uD83D\uDCCB Gerencie suas inscricoes e veja seu historico.",
            buttons = listOf(
                Button(id = "/minhas proximas", title = "Proximas"),
                Button(id = "/minhas historico", title = "Historico"),
                Button(id = "/start", title = "Menu Inicial")
            ),
            footer = "BoJogar"
        )
    }

    private fun showProximas(context: CommandContext, ws: WhatsAppService) {
        ws.sendList(
            to = context.from,
            header = "Minhas Proximas Peladas",
            body = "\uD83D\uDCC5 Peladas que voce esta inscrito:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Confirmadas",
                    rows = listOf(
                        ListRow(
                            id = "/minhas ver PEL01",
                            title = "Futevolei - Boa Viagem",
                            description = "Ter 12/08 - 19h | Pago"
                        ),
                        ListRow(
                            id = "/minhas ver PEL03",
                            title = "Futevolei - Candeias",
                            description = "Seg 18/08 - 17h | Pendente"
                        )
                    )
                ),
                ListSection(
                    title = "Lista de Espera",
                    rows = listOf(
                        ListRow(
                            id = "/minhas ver PEL02",
                            title = "Volei - Pina",
                            description = "Qua 13/08 - 18h | Posicao #2"
                        )
                    )
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun showDetalhes(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: "PEL01"

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Minha Inscricao — $codigo*\n\n")
                append("\uD83C\uDFD0 *Pelada:* Futevolei - Boa Viagem\n")
                append("\uD83D\uDCC5 *Data:* Terca, 12/08/2026\n")
                append("\u23F0 *Horario:* 19:00 - 21:00\n")
                append("\uD83D\uDCCD *Local:* Quadra Arena Beach\n")
                append("\uD83D\uDCB0 *Valor:* R$ 25,00\n")
                append("\u2705 *Status:* Confirmado\n")
                append("\uD83D\uDCB3 *Pagamento:* Pago")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/minhas cancelar $codigo", title = "Cancelar Inscricao"),
                Button(id = "/minhas proximas", title = "Voltar"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showCancelar(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: "PEL01"

        ws.sendButtons(
            to = context.from,
            header = "Cancelar Inscricao",
            body = "\u26A0\uFE0F Tem certeza que deseja cancelar sua inscricao na pelada *$codigo*?\n\nEssa acao nao pode ser desfeita.",
            buttons = listOf(
                Button(id = "/minhas cancelar_sim $codigo", title = "Sim, Cancelar"),
                Button(id = "/minhas ver $codigo", title = "Nao, Voltar")
            )
        )
    }

    private fun confirmarCancelamento(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: "PEL01"

        ws.sendMessage(
            context.from,
            buildString {
                append("\u274C *Inscricao Cancelada*\n\n")
                append("Sua inscricao na pelada *$codigo* foi cancelada com sucesso.\n")
                append("Caso tenha direito a reembolso, o organizador entrara em contato.")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer agora?",
            buttons = listOf(
                Button(id = "/peladas proximas", title = "Ver Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showHistorico(context: CommandContext, ws: WhatsAppService) {
        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCA *Historico de Peladas*\n\n")
                append("1. \u2705 Futevolei - Boa Viagem | 05/08 | R$ 25\n")
                append("2. \u2705 Volei - Pina | 01/08 | R$ 20\n")
                append("3. \u274C Futevolei - Candeias | 28/07 | Cancelado\n")
                append("4. \u2705 Futevolei - Boa Viagem | 22/07 | R$ 25\n\n")
                append("_Total: 4 peladas | 3 participadas_")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/minhas proximas", title = "Proximas Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
