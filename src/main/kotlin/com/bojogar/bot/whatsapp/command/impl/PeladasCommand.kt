package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.service.AuthorizationService
import com.bojogar.bot.service.JoinResult
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@Component
class PeladasCommand(
    private val peladaService: PeladaService,
    private val participantService: ParticipantService,
    private val authorizationService: AuthorizationService
) : BotCommand {

    override val name = "/peladas"

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        private val DATE_FMT_SHORT = DateTimeFormatter.ofPattern("EEE dd/MM - HH'h'", Locale("pt", "BR"))
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showMenu(context, whatsappService)
            "hoje" -> showHoje(context, whatsappService)
            "proximas" -> showProximas(context, whatsappService)
            "ver" -> showDetalhes(context, whatsappService)
            "participar" -> showParticipar(context, whatsappService)
            else -> showMenu(context, whatsappService)
        }
    }

    private fun showMenu(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "Peladas Disponiveis",
            body = "\uD83C\uDFD0 Veja as peladas abertas para participar!",
            buttons = listOf(
                Button(id = "/peladas hoje", title = "Pelada de Hoje"),
                Button(id = "/peladas proximas", title = "Proximas Peladas"),
                Button(id = "/entrar", title = "Entrar com Codigo")
            ),
            footer = "BoJogar"
        )
    }

    private fun showHoje(context: CommandContext, ws: WhatsAppService) {
        val today = LocalDate.now()
        val peladas = peladaService.findOpenPeladas()
            .filter { it.dataHora.toLocalDate() == today }

        if (peladas.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDCC5 Nenhuma pelada agendada para hoje.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/peladas proximas", title = "Proximas Peladas"),
                    Button(id = "/criar", title = "Criar Pelada"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        if (peladas.size == 1) {
            showDetalhesForPelada(context, ws, peladas.first())
        } else {
            val sections = listOf(
                ListSection(
                    title = "Hoje",
                    rows = peladas.take(10).map { p ->
                        val remaining = peladaService.getRemainingSlots(p)
                        ListRow(
                            id = "/peladas ver ${p.codigo}",
                            title = "${p.esporte.label} - ${p.local.take(20)}",
                            description = "${p.dataHora.format(DATE_FMT_SHORT)} | ${formatPrice(p)} | $remaining vagas"
                        )
                    }
                )
            )
            ws.sendList(
                to = context.from,
                header = "Peladas de Hoje",
                body = "\uD83C\uDFD0 ${peladas.size} pelada(s) hoje:",
                buttonLabel = "Ver Peladas",
                sections = sections,
                footer = "BoJogar"
            )
        }
    }

    private fun showProximas(context: CommandContext, ws: WhatsAppService) {
        val peladas = peladaService.findOpenPeladas()
            .sortedBy { it.dataHora }

        if (peladas.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDCC5 Nenhuma pelada aberta no momento.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/criar", title = "Criar Pelada"),
                    Button(id = "/entrar", title = "Entrar com Codigo"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        val weekFields = WeekFields.of(Locale("pt", "BR"))
        val currentWeek = LocalDate.now().get(weekFields.weekOfYear())
        val grouped = peladas.groupBy {
            val week = it.dataHora.toLocalDate().get(weekFields.weekOfYear())
            if (week == currentWeek) "Esta Semana" else "Proxima Semana"
        }

        val sections = grouped.map { (label, list) ->
            ListSection(
                title = label,
                rows = list.take(10).map { p ->
                    val remaining = peladaService.getRemainingSlots(p)
                    ListRow(
                        id = "/peladas ver ${p.codigo}",
                        title = "${p.esporte.label} - ${p.local.take(20)}",
                        description = "${p.dataHora.format(DATE_FMT_SHORT)} | ${formatPrice(p)} | $remaining vagas"
                    )
                }
            )
        }

        ws.sendList(
            to = context.from,
            header = "Proximas Peladas",
            body = "\uD83D\uDCC5 ${peladas.size} pelada(s) disponiveis:",
            buttonLabel = "Ver Peladas",
            sections = sections,
            footer = "BoJogar"
        )
    }

    private fun showDetalhes(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1)
        if (codigo == null) {
            showProximas(context, ws)
            return
        }

        val pelada = peladaService.findByCode(codigo)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$codigo* nao encontrada.")
            return
        }

        showDetalhesForPelada(context, ws, pelada)
    }

    private fun showDetalhesForPelada(context: CommandContext, ws: WhatsAppService, pelada: Pelada) {
        val remaining = peladaService.getRemainingSlots(pelada)
        val confirmed = peladaService.getConfirmedCount(pelada)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83C\uDFD0 *${pelada.esporte.label} — ${pelada.codigo}*\n\n")
                if (!pelada.descricao.isNullOrBlank()) append("\uD83D\uDCDD ${pelada.descricao}\n")
                append("\uD83D\uDCCD *Local:* ${pelada.local}\n")
                append("\uD83D\uDCC5 *Data:* ${pelada.dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}\n")
                append("\uD83D\uDC65 *Vagas:* $confirmed/${pelada.limiteJogadores} ($remaining restantes)\n")
                append("\uD83D\uDCB0 *Valor:* ${formatPrice(pelada)}\n")
                if (pelada.valorPorJogador > BigDecimal.ZERO && !pelada.chavePix.isNullOrBlank()) {
                    append("\uD83D\uDCF2 *Pix:* ${pelada.chavePix}\n")
                }
            }
        )

        val buttons = mutableListOf<Button>()
        buttons.add(Button(id = "/peladas participar ${pelada.codigo}", title = "Participar"))

        if (authorizationService.isAdminOrOwner(context.from, pelada.codigo)) {
            buttons.add(Button(id = "/gerenciar pelada ${pelada.codigo}", title = "Gerenciar"))
        } else {
            buttons.add(Button(id = "/peladas proximas", title = "Outras Peladas"))
        }

        if (buttons.size < 3) {
            buttons.add(Button(id = "/start", title = "Menu Inicial"))
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = buttons.take(3)
        )
    }

    private fun showParticipar(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1)
        if (codigo == null) {
            showProximas(context, ws)
            return
        }

        when (val result = participantService.join(context.from, codigo)) {
            is JoinResult.Confirmed -> {
                val pelada = peladaService.findByCode(codigo)!!
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *Inscricao Confirmada!*\n\n")
                        append("Pelada *${pelada.codigo}* — ${pelada.esporte.label}\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${pelada.dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}\n")
                        if (pelada.valorPorJogador > BigDecimal.ZERO) {
                            append("\n\uD83D\uDCB0 Valor: R$ ${pelada.valorPorJogador}\n")
                            if (!pelada.chavePix.isNullOrBlank()) {
                                append("\uD83D\uDCF2 Pix: ${pelada.chavePix}\n")
                            }
                        }
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/minhas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.Waitlisted -> {
                ws.sendMessage(
                    context.from,
                    "\uD83D\uDCCB *Lista de Espera*\n\nPelada *$codigo* lotada. Posicao #${result.position}.\nVoce sera notificado se uma vaga abrir!"
                )
            }
            is JoinResult.AlreadyJoined -> {
                ws.sendMessage(context.from, "\u26A0\uFE0F Voce ja esta inscrito nesta pelada!")
            }
            is JoinResult.PeladaClosed -> {
                ws.sendMessage(context.from, "\u274C Esta pelada nao esta aberta para inscricoes.")
            }
            is JoinResult.Error -> {
                ws.sendMessage(context.from, "\u274C ${result.message}")
            }
        }
    }

    private fun formatPrice(pelada: Pelada): String {
        return if (pelada.valorPorJogador > BigDecimal.ZERO) "R$ ${pelada.valorPorJogador}" else "Gratis"
    }
}
