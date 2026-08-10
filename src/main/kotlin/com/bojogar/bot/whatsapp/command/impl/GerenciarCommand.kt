package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPagamento
import com.bojogar.bot.service.*
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.ConversationState
import com.bojogar.bot.whatsapp.session.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Component
class GerenciarCommand(
    private val authorizationService: AuthorizationService,
    private val peladaService: PeladaService,
    private val participantService: ParticipantService,
    private val pagamentoService: PagamentoService,
    private val notificationService: NotificationService,
    private val sessionManager: SessionManager
) : BotCommand {

    override val name = "/gerenciar"
    override val aliases = listOf("/admin", "/manage")

    companion object {
        private val log = LoggerFactory.getLogger(GerenciarCommand::class.java)
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showManagedPeladas(context, whatsappService)
            "pelada" -> showPeladaAdmin(context, whatsappService)
            "participantes" -> showParticipantes(context, whatsappService)
            "confirmar_pgto" -> confirmarPagamento(context, whatsappService)
            "remover" -> showRemover(context, whatsappService)
            "remover_sim" -> confirmarRemocao(context, whatsappService)
            "financeiro" -> showFinanceiro(context, whatsappService)
            "editar" -> showEditar(context, whatsappService)
            "editar_campo" -> editarCampo(context, whatsappService)
            "cancelar" -> showCancelar(context, whatsappService)
            "cancelar_sim" -> confirmarCancelamento(context, whatsappService)
            else -> showManagedPeladas(context, whatsappService)
        }
    }

    private fun showManagedPeladas(context: CommandContext, ws: WhatsAppService) {
        val managed = authorizationService.getManagedPeladas(context.from)

        if (managed.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDCCB Voce nao gerencia nenhuma pelada.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/criar", title = "Criar Pelada"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        if (managed.size == 1) {
            showPeladaAdminFor(context, ws, managed.first().pelada.codigo)
            return
        }

        ws.sendList(
            to = context.from,
            header = "Minhas Peladas (Admin)",
            body = "\uD83D\uDD27 Selecione a pelada para gerenciar:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Peladas que voce gerencia",
                    rows = managed.take(10).map { p ->
                        ListRow(
                            id = "/gerenciar pelada ${p.pelada.codigo}",
                            title = "${p.pelada.esporte.label} - ${p.pelada.codigo}",
                            description = "${p.pelada.local.take(30)} | ${p.role.name}"
                        )
                    }
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun showPeladaAdmin(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao para gerenciar esta pelada.")
            return
        }

        showPeladaAdminFor(context, ws, code)
    }

    private fun showPeladaAdminFor(context: CommandContext, ws: WhatsAppService, code: String) {
        val pelada = peladaService.findByCode(code) ?: return
        val confirmed = peladaService.getConfirmedCount(pelada)
        val remaining = peladaService.getRemainingSlots(pelada)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD27 *Admin — ${pelada.codigo}*\n\n")
                append("\uD83C\uDFC6 ${pelada.esporte.label}\n")
                append("\uD83D\uDCCD ${pelada.local}\n")
                append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n")
                append("\uD83D\uDC65 $confirmed/${pelada.limiteJogadores} ($remaining vagas)\n")
                append("\uD83D\uDCCA Status: ${pelada.status.name}")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar participantes $code", title = "Participantes"),
                Button(id = "/gerenciar financeiro $code", title = "Financeiro"),
                Button(id = "/gerenciar cancelar $code", title = "Cancelar Pelada")
            )
        )
    }

    private fun showParticipantes(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getActiveParticipants(code)

        if (participants.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDC65 Nenhum participante nesta pelada.")
            return
        }

        val confirmed = participants.filter { it.status == ParticipantStatus.CONFIRMED }
        val waitlisted = participants.filter { it.status == ParticipantStatus.WAITLIST }

        val sections = mutableListOf<ListSection>()

        if (confirmed.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "Confirmados (${confirmed.size})",
                    rows = confirmed.take(10).map { p ->
                        val roleLabel = if (p.role != ParticipantRole.PLAYER) " [${p.role.name}]" else ""
                        ListRow(
                            id = "/gerenciar remover $code ${p.user.phone}",
                            title = "${p.displayName ?: p.user.name}$roleLabel",
                            description = PhoneUtils.formatPhoneDisplay(p.user.phone)
                        )
                    }
                )
            )
        }

        if (waitlisted.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "Lista de Espera (${waitlisted.size})",
                    rows = waitlisted.take(10).map { p ->
                        ListRow(
                            id = "/gerenciar remover $code ${p.user.phone}",
                            title = "#${p.waitlistPosition} ${p.displayName ?: p.user.name}",
                            description = PhoneUtils.formatPhoneDisplay(p.user.phone)
                        )
                    }
                )
            )
        }

        ws.sendList(
            to = context.from,
            header = "Participantes — $code",
            body = "\uD83D\uDC65 ${participants.size} participante(s). Selecione para remover:",
            buttonLabel = "Ver Lista",
            sections = sections,
            footer = "BoJogar"
        )
    }

    private fun showRemover(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return
        val phone = context.args.getOrNull(2) ?: return

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.user.phone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            return
        }

        ws.sendButtons(
            to = context.from,
            header = "Remover Participante",
            body = "\u26A0\uFE0F Remover *${target.displayName ?: target.user.name}* da pelada *$code*?",
            buttons = listOf(
                Button(id = "/gerenciar remover_sim $code $phone", title = "Sim, Remover"),
                Button(id = "/gerenciar participantes $code", title = "Voltar")
            )
        )
    }

    private fun confirmarRemocao(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return
        val phone = context.args.getOrNull(2) ?: return

        when (val result = participantService.removeParticipant(context.from, phone, code)) {
            is RemoveResult.Removed -> {
                ws.sendMessage(context.from, "\u2705 Participante removido com sucesso.")

                val pelada = peladaService.findByCode(code)
                val removedParticipant = participantService.getParticipants(code).find { it.user.phone == phone }
                if (pelada != null && removedParticipant != null) {
                    notificationService.notifyParticipantRemoved(removedParticipant, pelada)
                }

                if (result.promoted != null) {
                    val promotedPelada = peladaService.findByCode(code)
                    if (promotedPelada != null) {
                        notificationService.notifyWaitlistPromotion(result.promoted, promotedPelada)
                    }
                    ws.sendMessage(
                        context.from,
                        "\uD83D\uDD04 ${result.promoted.displayName ?: result.promoted.user.name} promovido da lista de espera."
                    )
                }
            }
            is RemoveResult.NotFound -> ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            is RemoveResult.Unauthorized -> ws.sendMessage(context.from, "\u274C Sem permissao.")
            is RemoveResult.Error -> ws.sendMessage(context.from, "\u274C ${result.message}")
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar participantes $code", title = "Ver Participantes"),
                Button(id = "/gerenciar pelada $code", title = "Menu Admin")
            )
        )
    }

    private fun confirmarPagamento(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return
        val phone = context.args.getOrNull(2) ?: return

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.user.phone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            return
        }

        try {
            pagamentoService.confirmPayment(target.id!!, context.from)
            ws.sendMessage(
                context.from,
                "\u2705 Pagamento de *${target.displayName ?: target.user.name}* confirmado!"
            )
        } catch (e: Exception) {
            ws.sendMessage(context.from, "\u274C ${e.message}")
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar financeiro $code", title = "Ver Financeiro"),
                Button(id = "/gerenciar pelada $code", title = "Menu Admin")
            )
        )
    }

    private fun showFinanceiro(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val pelada = peladaService.findByCode(code)
        if (pelada == null || pelada.valorPorJogador <= BigDecimal.ZERO) {
            ws.sendMessage(context.from, "\uD83D\uDCB0 Esta pelada e gratuita.")
            ws.sendButtons(
                to = context.from,
                body = "Voltar:",
                buttons = listOf(Button(id = "/gerenciar pelada $code", title = "Menu Admin"))
            )
            return
        }

        val payments = pagamentoService.getPaymentStatus(code)
        val paid = payments.count { it.status == StatusPagamento.CONFIRMADO }
        val pending = payments.count { it.status == StatusPagamento.PENDENTE }
        val totalCollected = payments.filter { it.status == StatusPagamento.CONFIRMADO }.sumOf { it.amount }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCB0 *Financeiro — $code*\n\n")
                append("\u2705 *Pagos:* $paid\n")
                append("\u23F3 *Pendentes:* $pending\n")
                append("\uD83D\uDCB5 *Total:* R$ $totalCollected\n\n")

                if (payments.isNotEmpty()) {
                    payments.forEach { p ->
                        val icon = if (p.status == StatusPagamento.CONFIRMADO) "\u2705" else "\u23F3"
                        append("$icon ${p.participantName} — R$ ${p.amount}\n")
                    }
                }
            }
        )

        val unpaid = pagamentoService.getUnpaidParticipants(code)
        if (unpaid.isNotEmpty()) {
            ws.sendList(
                to = context.from,
                header = "Confirmar Pagamentos",
                body = "\u23F3 Selecione para confirmar pagamento:",
                buttonLabel = "Ver Pendentes",
                sections = listOf(
                    ListSection(
                        title = "Pagamento Pendente",
                        rows = unpaid.take(10).map { p ->
                            ListRow(
                                id = "/gerenciar confirmar_pgto $code ${p.user.phone}",
                                title = p.displayName ?: p.user.name,
                                description = "R$ ${pelada.valorPorJogador} | Pendente"
                            )
                        }
                    )
                ),
                footer = "BoJogar"
            )
        } else {
            ws.sendButtons(
                to = context.from,
                body = "Voltar:",
                buttons = listOf(Button(id = "/gerenciar pelada $code", title = "Menu Admin"))
            )
        }
    }

    private fun showEditar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: return

        ws.sendMessage(
            context.from,
            buildString {
                append("\u270F\uFE0F *Editar Pelada — $code*\n\n")
                append("Campos editaveis:\n")
                append("1. Local: ${pelada.local}\n")
                append("2. Data: ${pelada.dataHora.format(DATE_FMT)}\n")
                append("3. Limite: ${pelada.limiteJogadores} jogadores\n")
                append("4. Valor: R$ ${pelada.valorPorJogador}\n")
                append("5. Descricao: ${pelada.descricao ?: "-"}\n")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Selecione o campo para editar:",
            buttons = listOf(
                Button(id = "/gerenciar editar_campo $code local", title = "Editar Local"),
                Button(id = "/gerenciar editar_campo $code limite", title = "Editar Limite"),
                Button(id = "/gerenciar pelada $code", title = "Voltar")
            )
        )
    }

    private fun editarCampo(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return
        val field = context.args.getOrNull(2) ?: return
        val value = context.args.drop(3).joinToString(" ")

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: return

        if (value.isBlank()) {
            sessionManager.setCurrentPelada(context.from, code, ConversationState.EDITING_PELADA)
            sessionManager.updateSession(context.from, "field", field, field)

            val label = when (field) {
                "local" -> "novo local"
                "limite" -> "novo limite de jogadores"
                "valor" -> "novo valor por jogador"
                "descricao" -> "nova descricao"
                else -> "novo valor"
            }
            ws.sendMessage(context.from, "\u270F\uFE0F Digite o $label:")
            return
        }

        try {
            when (field) {
                "local" -> pelada.local = value
                "limite" -> {
                    val limite = value.toIntOrNull() ?: throw IllegalArgumentException("Numero invalido")
                    if (limite < 2) throw IllegalArgumentException("Minimo 2 jogadores")
                    pelada.limiteJogadores = limite
                }
                "valor" -> {
                    val valor = value.replace(",", ".").toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("Valor invalido")
                    pelada.valorPorJogador = valor
                }
                "descricao" -> pelada.descricao = value
                else -> throw IllegalArgumentException("Campo desconhecido: $field")
            }

            peladaService.updateStatus(code, pelada.status, context.from) // just saves
            sessionManager.clear(context.from)
            ws.sendMessage(context.from, "\u2705 Campo *$field* atualizado!")

            notificationService.notifyParticipants(
                code,
                "\uD83D\uDD14 A pelada *$code* foi atualizada. Confira os novos detalhes!"
            )
        } catch (e: Exception) {
            ws.sendMessage(context.from, "\u274C ${e.message}")
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar editar $code", title = "Editar Mais"),
                Button(id = "/gerenciar pelada $code", title = "Menu Admin")
            )
        )
    }

    private fun showCancelar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return

        if (!authorizationService.isOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Apenas o organizador pode cancelar.")
            return
        }

        val participants = participantService.getActiveParticipants(code)

        ws.sendButtons(
            to = context.from,
            header = "Cancelar Pelada",
            body = buildString {
                append("\u26A0\uFE0F *Tem certeza?*\n\n")
                append("Cancelar a pelada *$code* afetara ${participants.size} participante(s).\n\n")
                append("*Esta acao nao pode ser desfeita.*")
            },
            buttons = listOf(
                Button(id = "/gerenciar cancelar_sim $code", title = "Sim, Cancelar"),
                Button(id = "/gerenciar pelada $code", title = "Nao, Voltar")
            )
        )
    }

    private fun confirmarCancelamento(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return

        if (!authorizationService.isOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Apenas o organizador pode cancelar.")
            return
        }

        try {
            val participants = participantService.getActiveParticipants(code)
            val pelada = peladaService.cancel(code, context.from)

            notificationService.notifyPeladaCancelled(pelada, participants)

            ws.sendMessage(
                context.from,
                "\u274C *Pelada Cancelada*\n\nA pelada *$code* foi cancelada. ${participants.size} participante(s) notificado(s)."
            )
        } catch (e: Exception) {
            ws.sendMessage(context.from, "\u274C Erro: ${e.message}")
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/criar", title = "Criar Pelada"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
