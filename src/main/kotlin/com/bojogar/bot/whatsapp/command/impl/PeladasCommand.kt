package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component

@Component
class PeladasCommand : BotCommand {

    override val name = "/peladas"

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showMenu(context, whatsappService)
            "hoje" -> showHoje(context, whatsappService)
            "proximas" -> showProximas(context, whatsappService)
            "ver" -> showDetalhes(context, whatsappService)
            "participar" -> showParticipar(context, whatsappService)
            "espera" -> showEspera(context, whatsappService)
            else -> showMenu(context, whatsappService)
        }
    }

    private fun showMenu(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "Peladas Disponiveis",
            body = "\uD83C\uDFD0 Veja as peladas abertas para voce participar!",
            buttons = listOf(
                Button(id = "/peladas hoje", title = "Pelada de Hoje"),
                Button(id = "/peladas proximas", title = "Proximas Peladas")
            ),
            footer = "BoJogar"
        )
    }

    private fun showHoje(context: CommandContext, ws: WhatsAppService) {
        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83C\uDFD0 *Pelada de Hoje*\n\n")
                append("\uD83D\uDCCD *Local:* Quadra Arena Beach - Boa Viagem\n")
                append("\uD83D\uDCC5 *Data:* Hoje\n")
                append("\u23F0 *Horario:* 19:00\n")
                append("\uD83D\uDCB0 *Valor:* R$ 25,00\n")
                append("\uD83D\uDC65 *Vagas:* 3/12 restantes\n")
                append("\uD83C\uDFC6 *Esporte:* Futevolei\n\n")
                append("_Organizador: @Lucas_")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Deseja participar desta pelada?",
            buttons = listOf(
                Button(id = "/peladas participar HOJ01", title = "Participar"),
                Button(id = "/peladas", title = "Voltar")
            )
        )
    }

    private fun showProximas(context: CommandContext, ws: WhatsAppService) {
        ws.sendList(
            to = context.from,
            header = "Proximas Peladas",
            body = "\uD83D\uDCC5 Confira as peladas agendadas para os proximos dias:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Esta Semana",
                    rows = listOf(
                        ListRow(
                            id = "/peladas ver PEL01",
                            title = "Futevolei - Boa Viagem",
                            description = "Ter 12/08 - 19h | R$ 25 | 3 vagas"
                        ),
                        ListRow(
                            id = "/peladas ver PEL02",
                            title = "Volei - Pina",
                            description = "Qua 13/08 - 18h | R$ 20 | 5 vagas"
                        )
                    )
                ),
                ListSection(
                    title = "Proxima Semana",
                    rows = listOf(
                        ListRow(
                            id = "/peladas ver PEL03",
                            title = "Futevolei - Candeias",
                            description = "Seg 18/08 - 17h | R$ 30 | 8 vagas"
                        ),
                        ListRow(
                            id = "/peladas ver PEL04",
                            title = "Volei - Piedade",
                            description = "Ter 19/08 - 19h | Gratis | 6 vagas"
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
                append("\uD83C\uDFD0 *Detalhes da Pelada — $codigo*\n\n")
                append("\uD83D\uDCCD *Local:* Quadra Arena Beach - Boa Viagem\n")
                append("\uD83D\uDCC5 *Data:* Terca, 12/08/2026\n")
                append("\u23F0 *Horario:* 19:00 - 21:00\n")
                append("\uD83D\uDCB0 *Valor:* R$ 25,00 por jogador\n")
                append("\uD83D\uDC65 *Vagas:* 9/12 preenchidas (3 restantes)\n")
                append("\uD83C\uDFC6 *Esporte:* Futevolei\n")
                append("\uD83D\uDD11 *Pix:* pix@arena.com\n\n")
                append("_Organizador: @Lucas_")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/peladas participar $codigo", title = "Participar"),
                Button(id = "/peladas proximas", title = "Outras Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showParticipar(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: "PEL01"

        ws.sendMessage(
            context.from,
            buildString {
                append("\u2705 *Vaga disponivel!*\n\n")
                append("Pelada *$codigo* — Futevolei em Boa Viagem\n")
                append("\uD83D\uDCB0 Valor: *R$ 25,00*\n\n")
                append("\uD83D\uDCF2 *Chave Pix:* pix@arena.com\n\n")
                append("Realize o pagamento e envie o comprovante para confirmar sua vaga.")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Pagamento realizado?",
            buttons = listOf(
                Button(id = "/peladas ver $codigo", title = "Ver Detalhes"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showEspera(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: "PEL01"

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Lista de Espera*\n\n")
                append("A pelada *$codigo* esta lotada.\n")
                append("Voce foi adicionado na *posicao #2* da lista de espera.\n\n")
                append("Voce sera notificado caso uma vaga abra!")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/peladas proximas", title = "Outras Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
