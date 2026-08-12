package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.service.AuthorizationService
import com.bojogar.bot.service.LeaveResult
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PagamentoService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.UxCopy
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class MinhasPeladasCommand(
    private val participantService: ParticipantService,
    private val peladaService: PeladaService,
    private val authorizationService: AuthorizationService,
    private val pagamentoService: PagamentoService
) : BotCommand {

    override val name = "/minhas"

    companion object {
        private val log = LoggerFactory.getLogger(MinhasPeladasCommand::class.java)
        private val ZONE_BR = ZoneId.of("America/Sao_Paulo")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showMenu(context, whatsappService)
            "proximas" -> showProximas(context, whatsappService)
            "ver" -> showDetalhes(context, whatsappService)
            "cancelar" -> showCancelar(context, whatsappService)
            "cancelar_sim" -> confirmarCancelamento(context, whatsappService)
            "confirmados" -> showConfirmados(context, whatsappService)
            "historico" -> showHistorico(context, whatsappService)
            else -> showMenu(context, whatsappService)
        }
    }

    private fun showMenu(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "\uD83D\uDCCB Minhas Peladas",
            body = "Gerencie suas inscrições e acompanhe seu histórico.",
            buttons = listOf(
                Button(id = "/minhas proximas", title = "Próximas"),
                Button(id = "/minhas historico", title = "Histórico"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showProximas(context: CommandContext, ws: WhatsAppService) {
        log.info("Listando próximas peladas de {}", context.from)
        val participations = participantService.getUserParticipations(context.from)
        val peladaMap = fetchPeladaMap(participations)

        val upcoming = participations.filter { p ->
            val pelada = peladaMap[p.peladaCodigo]
            pelada != null &&
                pelada.dataHora.isAfter(LocalDateTime.now(ZONE_BR)) &&
                pelada.status in listOf("OPEN", "FULL")
        }

        if (upcoming.isEmpty()) {
            ws.sendButtons(
                to = context.from,
                body = "\uD83D\uDCC5 Você não está inscrito em nenhuma pelada no momento.",
                buttons = listOf(
                    Button(id = "/entrar", title = "Entrar com Código"),
                    Button(id = "/criar", title = "Criar Pelada"),
                    Button(id = "/start", title = "Menu")
                )
            )
            return
        }

        val confirmed = upcoming.filter { it.status == "CONFIRMED" }
        val pendingPayment = upcoming.filter { it.status == "PENDING_PAYMENT" }
        val waitlisted = upcoming.filter { it.status == "WAITLIST" }

        val sections = mutableListOf<ListSection>()

        if (confirmed.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\u2705 Confirmadas (${confirmed.size})",
                    rows = confirmed.take(10).mapNotNull { p ->
                        val pel = peladaMap[p.peladaCodigo] ?: return@mapNotNull null
                        ListRow(
                            id = "/minhas ver ${p.peladaCodigo}",
                            title = "${pel.esporteLabel} \u2014 ${pel.local.take(20)}",
                            description = "${UxCopy.formatDateCompact(pel.dataHora)} \u00B7 ${UxCopy.statusJogadorShort("CONFIRMED")}"
                        )
                    }
                )
            )
        }

        if (pendingPayment.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\u23F3 Pendentes (${pendingPayment.size})",
                    rows = pendingPayment.take(10).mapNotNull { p ->
                        val pel = peladaMap[p.peladaCodigo] ?: return@mapNotNull null
                        ListRow(
                            id = "/minhas ver ${p.peladaCodigo}",
                            title = "${pel.esporteLabel} \u2014 ${pel.local.take(20)}",
                            description = "${UxCopy.formatDateCompact(pel.dataHora)} \u00B7 ${UxCopy.statusJogadorShort("PENDING_PAYMENT")}"
                        )
                    }
                )
            )
        }

        if (waitlisted.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\uD83D\uDD52 Espera (${waitlisted.size})",
                    rows = waitlisted.take(10).mapNotNull { p ->
                        val pel = peladaMap[p.peladaCodigo] ?: return@mapNotNull null
                        ListRow(
                            id = "/minhas ver ${p.peladaCodigo}",
                            title = "${pel.esporteLabel} \u2014 ${pel.local.take(20)}",
                            description = "${UxCopy.formatDateCompact(pel.dataHora)} \u00B7 Posição #${p.waitlistPosition ?: "?"}"
                        )
                    }
                )
            )
        }

        ws.sendList(
            to = context.from,
            header = "\uD83D\uDCC5 Próximas Peladas",
            body = "Você tem *${upcoming.size}* pelada(s) agendada(s):",
            buttonLabel = "Ver Peladas",
            sections = sections
        )
    }

    private fun showDetalhes(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo detalhes da inscrição de {} na pelada {}", context.from, context.args.getOrNull(1))
        val codigo = context.args.getOrNull(1)
        if (codigo == null) {
            showProximas(context, ws)
            return
        }

        val pelada = peladaService.findByCode(codigo)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$codigo* não encontrada.")
            return
        }

        val participants = participantService.getParticipants(codigo)
        val normalized = PhoneUtils.normalizePhone(context.from)
        val myParticipation = participants.find { it.userPhone == normalized }
        val statusLabel = if (myParticipation != null) {
            UxCopy.statusJogador(myParticipation.status)
        } else {
            "\u2796 Não inscrito"
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Minha Inscrição \u2014 $codigo*\n\n")
                append("\uD83C\uDFC6 *Pelada:* ${pelada.esporteLabel} \u2014 ${pelada.local}\n")
                append("\uD83D\uDCC5 *Data:* ${UxCopy.formatDate(pelada.dataHora)}\n")
                append("\uD83D\uDCB0 *Valor:* ${UxCopy.formatPrice(pelada.valorPorJogador)}\n")
                val organizador = if (!pelada.createdByName.isNullOrBlank()) pelada.createdByName else pelada.createdByPhone
                append("\uD83D\uDC64 *Organizador:* $organizador\n")
                append("\uD83D\uDCCA *Status:* $statusLabel")
            }
        )

        val buttons = mutableListOf<Button>()

        val pendingPayment = if (pelada.valorPorJogador > BigDecimal.ZERO) {
            pagamentoService.findPendingPaymentForUser(context.from, codigo)
        } else null

        if (pendingPayment != null) {
            buttons.add(Button(id = "/pagar gerar $codigo", title = "Pagar via PIX"))
        }

        if (authorizationService.isAdminOrOwner(context.from, codigo)) {
            buttons.add(Button(id = "/gerenciar pelada $codigo", title = "Gerenciar"))
        } else if (pendingPayment == null) {
            buttons.add(Button(id = "/minhas cancelar $codigo", title = "Cancelar Inscrição"))
        }
        if (buttons.size < 3) buttons.add(Button(id = "/minhas confirmados $codigo", title = "Ver Confirmados"))
        if (buttons.size < 3 && !authorizationService.isAdminOrOwner(context.from, codigo)) {
            buttons.add(Button(id = "/entrar $codigo organizador", title = "Msg Organizador"))
        }
        if (buttons.size < 3) buttons.add(Button(id = "/minhas proximas", title = "Voltar"))

        ws.sendButtons(to = context.from, body = "Escolha uma ação:", buttons = buttons.take(3))
    }

    private fun showCancelar(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: return showProximas(context, ws)
        ws.sendButtons(
            to = context.from,
            header = "Cancelar Inscrição",
            body = "\u26A0\uFE0F Tem certeza que deseja cancelar sua inscrição na pelada *$codigo*?\n\n_Essa ação não pode ser desfeita._",
            buttons = listOf(
                Button(id = "/minhas cancelar_sim $codigo", title = "Sim, Cancelar"),
                Button(id = "/minhas ver $codigo", title = "Não, Voltar")
            )
        )
    }

    private fun confirmarCancelamento(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: return showProximas(context, ws)
        log.info("Cancelando inscrição de {} na pelada {}", context.from, codigo)

        when (val result = participantService.leave(context.from, codigo)) {
            is LeaveResult.Left -> {
                log.info("Inscrição cancelada: {} saiu da pelada {}", context.from, codigo)
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u274C *Inscrição Cancelada*\n\n")
                        append("Sua inscrição na pelada *$codigo* foi cancelada.\n\n")
                        append("Caso tenha direito a reembolso, entre em contato com o organizador.")
                    }
                )
                if (result.promoted != null) {
                    log.info("Jogador {} promovido da lista de espera na pelada {}", result.promoted.userPhone, codigo)
                }
            }
            is LeaveResult.NotFound -> {
                ws.sendMessage(context.from, "\u26A0\uFE0F Inscrição não encontrada.")
            }
            is LeaveResult.Error -> {
                ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
            }
        }

        ws.sendButtons(
            to = context.from,
            body = "Quer participar de outra pelada?",
            buttons = listOf(
                Button(id = "/entrar", title = "Entrar com Código"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showConfirmados(context: CommandContext, ws: WhatsAppService) {
        val codigo = context.args.getOrNull(1) ?: return showProximas(context, ws)
        log.info("Listando confirmados da pelada {} para {}", codigo, context.from)

        val pelada = peladaService.findByCode(codigo)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$codigo* não encontrada.")
            return
        }

        val participants = participantService.getActiveParticipants(codigo)
        val confirmed = participants.filter { it.status == "CONFIRMED" }

        if (confirmed.isEmpty()) {
            ws.sendButtons(
                to = context.from,
                body = "\uD83D\uDC65 Nenhum jogador confirmado nesta pelada ainda.",
                buttons = listOf(
                    Button(id = "/minhas ver $codigo", title = "Voltar"),
                    Button(id = "/start", title = "Menu")
                )
            )
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDC65 *Confirmados \u2014 $codigo*\n\n")
                confirmed.forEachIndexed { i, p ->
                    val name = p.displayName ?: p.userName
                    val tag = if (p.role == "OWNER") " (Organizador)" else ""
                    append("${i + 1}. $name$tag\n")
                }
                val limite = if (pelada.limiteJogadores > 0) "/${pelada.limiteJogadores}" else ""
                append("\n_Total: ${confirmed.size}$limite confirmado(s)_")
            }
        )

        val confirmadosButtons = mutableListOf(
            Button(id = "/minhas ver $codigo", title = "Voltar")
        )
        if (!authorizationService.isAdminOrOwner(context.from, codigo)) {
            confirmadosButtons.add(Button(id = "/entrar $codigo organizador", title = "Msg Organizador"))
        }
        confirmadosButtons.add(Button(id = "/start", title = "Menu"))

        ws.sendButtons(
            to = context.from,
            body = "Mais alguma coisa?",
            buttons = confirmadosButtons.take(3)
        )
    }

    private fun showHistorico(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo histórico de peladas de {}", context.from)
        val all = participantService.getUserParticipations(context.from, activeOnly = false)
        val peladaMap = fetchPeladaMap(all)

        val past = all.filter { p ->
            val pelada = peladaMap[p.peladaCodigo]
            pelada != null && (
                pelada.dataHora.isBefore(LocalDateTime.now(ZONE_BR)) ||
                    pelada.status in listOf("FINISHED", "CANCELLED") ||
                    p.status in listOf("CANCELLED", "REMOVED")
                )
        }

        if (past.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDCCA *Histórico*\n\nVocê ainda não participou de nenhuma pelada.")
        } else {
            ws.sendMessage(
                context.from,
                buildString {
                    append("\uD83D\uDCCA *Histórico de Peladas*\n\n")
                    past.take(10).forEachIndexed { i, p ->
                        val pel = peladaMap[p.peladaCodigo] ?: return@forEachIndexed
                        val icon = if (p.status == "CONFIRMED") "\u2705" else "\u274C"
                        append("${i + 1}. $icon ${pel.esporteLabel} \u2014 ${pel.local.take(15)} \u00B7 ${UxCopy.formatDateCompact(pel.dataHora)} \u00B7 ${UxCopy.formatPrice(pel.valorPorJogador)}\n")
                    }
                    append("\n_Total: ${past.size} pelada(s)_")
                }
            )
        }

        ws.sendButtons(
            to = context.from,
            body = "Mais alguma coisa?",
            buttons = listOf(
                Button(id = "/minhas proximas", title = "Próximas Peladas"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun fetchPeladaMap(participations: List<ParticipantResponse>): Map<String, PeladaResponse> {
        return participations.map { it.peladaCodigo }.distinct()
            .mapNotNull { code -> peladaService.findByCode(code)?.let { code to it } }
            .toMap()
    }
}
