package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.dto.request.UpdatePeladaRequest
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class GerenciarCommand(
    private val authorizationService: AuthorizationService,
    private val peladaService: PeladaService,
    private val participantService: ParticipantService,
    private val pagamentoService: PagamentoService,
    private val notificationService: NotificationService,
    private val sessionManager: SessionManager,
    private val whatsAppProperties: WhatsAppProperties
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
            "convidar" -> showConvidar(context, whatsappService)
            "saque" -> solicitarSaque(context, whatsappService)
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
            showPeladaAdminFor(context, ws, managed.first().peladaCodigo)
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
                    rows = managed.take(10).mapNotNull { p ->
                        val pel = peladaService.findByCode(p.peladaCodigo)
                        if (pel != null) {
                            ListRow(
                                id = "/gerenciar pelada ${p.peladaCodigo}",
                                title = "${pel.esporteLabel} - ${p.peladaCodigo}",
                                description = "${pel.local.take(30)} | ${p.role}"
                            )
                        } else null
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
        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada.")
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD27 *Admin — ${pelada.codigo}*\n\n")
                append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                append("\uD83D\uDCCD ${pelada.local}\n")
                append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n")
                if (pelada.limiteJogadores == 0) {
                    append("\uD83D\uDC65 ${pelada.confirmedCount} confirmados (Sem limite)\n")
                } else {
                    append("\uD83D\uDC65 ${pelada.confirmedCount}/${pelada.limiteJogadores} (${pelada.remainingSlots} vagas)\n")
                }
                append("\uD83D\uDCCA Status: ${pelada.status}")
            }
        )

        val sections = mutableListOf<ListSection>()
        sections.add(
            ListSection(
                title = "Gestao",
                rows = listOf(
                    ListRow(id = "/gerenciar participantes $code", title = "Participantes", description = "${pelada.confirmedCount} confirmados"),
                    ListRow(id = "/gerenciar financeiro $code", title = "Financeiro", description = "Pagamentos e valores"),
                    ListRow(id = "/gerenciar convidar $code", title = "Convidar Amigos", description = "Gerar link de convite")
                )
            )
        )
        sections.add(
            ListSection(
                title = "Configuracao",
                rows = listOf(
                    ListRow(id = "/gerenciar editar $code", title = "Editar Pelada", description = "Local, data, valor..."),
                    ListRow(id = "/gerenciar cancelar $code", title = "Cancelar Pelada", description = "Cancelar e notificar todos")
                )
            )
        )

        ws.sendList(
            to = context.from,
            body = "O que deseja fazer?",
            buttonLabel = "Ver Opcoes",
            sections = sections,
            footer = "BoJogar"
        )
    }

    private fun showParticipantes(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getActiveParticipants(code)

        if (participants.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDC65 Nenhum participante nesta pelada.")
            return
        }

        val confirmed = participants.filter { it.status == "CONFIRMED" }
        val pendingPayment = participants.filter { it.status == "PENDING_PAYMENT" }
        val waitlisted = participants.filter { it.status == "WAITLIST" }
        val pelada = peladaService.findByCode(code)

        val sections = mutableListOf<ListSection>()

        if (confirmed.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "Confirmados (${confirmed.size})",
                    rows = confirmed.take(10).map { p ->
                        val roleLabel = if (p.role != "PLAYER") " [${p.role}]" else ""
                        ListRow(
                            id = "/gerenciar remover $code ${p.userPhone}",
                            title = "${p.displayName ?: p.userName}$roleLabel",
                            description = "${PhoneUtils.formatPhoneDisplay(p.userPhone)} | Pago"
                        )
                    }
                )
            )
        }

        if (pendingPayment.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "Aguardando Pagamento (${pendingPayment.size})",
                    rows = pendingPayment.take(10).map { p ->
                        val roleLabel = if (p.role != "PLAYER") " [${p.role}]" else ""
                        ListRow(
                            id = "/gerenciar remover $code ${p.userPhone}",
                            title = "${p.displayName ?: p.userName}$roleLabel",
                            description = "${PhoneUtils.formatPhoneDisplay(p.userPhone)} | Pendente"
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
                            id = "/gerenciar remover $code ${p.userPhone}",
                            title = "#${p.waitlistPosition} ${p.displayName ?: p.userName}",
                            description = PhoneUtils.formatPhoneDisplay(p.userPhone)
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
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showParticipantes(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.userPhone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            return
        }

        ws.sendButtons(
            to = context.from,
            header = "Remover Participante",
            body = "\u26A0\uFE0F Remover *${target.displayName ?: target.userName}* da pelada *$code*?",
            buttons = listOf(
                Button(id = "/gerenciar remover_sim $code $phone", title = "Sim, Remover"),
                Button(id = "/gerenciar participantes $code", title = "Voltar")
            )
        )
    }

    private fun confirmarRemocao(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showParticipantes(context, ws)

        when (val result = participantService.removeParticipant(context.from, phone, code)) {
            is RemoveResult.Removed -> {
                ws.sendMessage(context.from, "\u2705 Participante removido com sucesso.")

                val pelada = peladaService.findByCode(code)
                val removedParticipant = participantService.getParticipants(code).find { it.userPhone == phone }
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
                        "\uD83D\uDD04 ${result.promoted.displayName ?: result.promoted.userName} promovido da lista de espera."
                    )
                }
            }
            is RemoveResult.NotFound -> ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            is RemoveResult.Unauthorized -> ws.sendMessage(context.from, "\u274C Sem permissao.")
            is RemoveResult.Error -> ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
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
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.userPhone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante nao encontrado.")
            return
        }

        try {
            pagamentoService.confirmPayment(target.id, context.from)
            ws.sendMessage(
                context.from,
                "\u2705 Pagamento de *${target.displayName ?: target.userName}* confirmado!"
            )
        } catch (e: Exception) {
            log.error("Error confirming payment in pelada {}: {}", code, e.message, e)
            ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
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
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

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

        val payments = pagamentoService.getPaymentsByPelada(code)
        val paid = payments.count { it.status == "CONFIRMADO" }
        val pending = payments.count { it.status == "PENDENTE" }
        val totalCollected = payments.filter { it.status == "CONFIRMADO" }.sumOf { it.valor }
        val walletBalance = pagamentoService.getWalletBalance(code)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCB0 *Financeiro — $code*\n\n")
                append("\u2705 *Pagos:* $paid\n")
                append("\u23F3 *Pendentes:* $pending\n")
                append("\uD83D\uDCB5 *Total Arrecadado:* R$ $totalCollected\n")
                append("\uD83D\uDCB3 *Saldo Disponivel:* R$ $walletBalance\n")
                append("_(ja descontadas as taxas)_\n\n")

                if (payments.isNotEmpty()) {
                    payments.forEach { p ->
                        val icon = if (p.status == "CONFIRMADO") "\u2705" else "\u23F3"
                        append("$icon ${p.participantName} — R$ ${p.valor}\n")
                    }
                }
            }
        )

        val unpaid = pagamentoService.getUnpaidParticipants(code)
        if (unpaid.isNotEmpty()) {
            ws.sendList(
                to = context.from,
                header = "Acoes Financeiras",
                body = "Selecione uma opcao:",
                buttonLabel = "Ver Opcoes",
                sections = listOf(
                    ListSection(
                        title = "Saque",
                        rows = listOf(
                            ListRow(
                                id = "/gerenciar saque $code",
                                title = "Solicitar Saque",
                                description = "Saldo: R$ $walletBalance"
                            )
                        )
                    ),
                    ListSection(
                        title = "Pagamento Pendente",
                        rows = unpaid.take(9).map { p ->
                            ListRow(
                                id = "/gerenciar confirmar_pgto $code ${p.userPhone}",
                                title = p.displayName ?: p.userName,
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
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/gerenciar saque $code", title = "Solicitar Saque"),
                    Button(id = "/gerenciar pelada $code", title = "Menu Admin")
                )
            )
        }
    }

    private fun showEditar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada.")
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\u270F\uFE0F *Editar Pelada — $code*\n\n")
                append("Campos editaveis:\n")
                append("1. Local: ${pelada.local}\n")
                append("2. Data: ${pelada.dataHora.format(DATE_FMT)}\n")
                val limiteDisplay = if (pelada.limiteJogadores == 0) "Sem limite" else "${pelada.limiteJogadores} jogadores"
                append("3. Limite: $limiteDisplay\n")
                append("4. Valor: R$ ${pelada.valorPorJogador}\n")
                append("5. Descricao: ${pelada.descricao ?: "-"}\n")
            }
        )

        ws.sendList(
            to = context.from,
            body = "Selecione o campo para editar:",
            buttonLabel = "Ver Campos",
            sections = listOf(
                ListSection(
                    title = "Campos Editaveis",
                    rows = listOf(
                        ListRow(id = "/gerenciar editar_campo $code local", title = "Local", description = pelada.local.take(30)),
                        ListRow(id = "/gerenciar editar_campo $code dataHora", title = "Data/Hora", description = pelada.dataHora.format(DATE_FMT)),
                        ListRow(id = "/gerenciar editar_campo $code limite", title = "Limite de Jogadores", description = if (pelada.limiteJogadores == 0) "Sem limite" else "${pelada.limiteJogadores} jogadores"),
                        ListRow(id = "/gerenciar editar_campo $code valor", title = "Valor por Jogador", description = "R$ ${pelada.valorPorJogador}"),
                        ListRow(id = "/gerenciar editar_campo $code descricao", title = "Descricao", description = (pelada.descricao ?: "-").take(30)),
                        ListRow(id = "/gerenciar editar_campo $code chavePix", title = "Chave Pix", description = (pelada.chavePix ?: "-").take(30))
                    )
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun editarCampo(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val field = context.args.getOrNull(2) ?: return showEditar(context, ws)
        val value = context.args.drop(3).joinToString(" ")

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        if (value.isBlank()) {
            sessionManager.setCurrentPelada(context.from, code, ConversationState.EDITING_PELADA)
            sessionManager.updateSession(context.from, "field", field, field)

            val label = when (field) {
                "local" -> "novo local"
                "limite" -> "novo limite de jogadores (ou 0 para sem limite)"
                "valor" -> "novo valor por jogador"
                "descricao" -> "nova descricao"
                "dataHora" -> "nova data e horario (DD/MM HH:MM)"
                "chavePix" -> "nova chave Pix"
                else -> "novo valor"
            }
            ws.sendMessage(context.from, "\u270F\uFE0F Digite o $label:")
            return
        }

        try {
            val request = when (field) {
                "local" -> UpdatePeladaRequest(local = value)
                "limite" -> {
                    val limite = value.toIntOrNull() ?: throw IllegalArgumentException("Numero invalido")
                    if (limite != 0 && limite < 2) throw IllegalArgumentException("Minimo 2 jogadores (ou 0 para sem limite)")
                    UpdatePeladaRequest(limiteJogadores = limite)
                }
                "valor" -> {
                    val valor = value.replace(",", ".").toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("Valor invalido")
                    if (valor > java.math.BigDecimal.ZERO && valor < java.math.BigDecimal(5)) {
                        throw IllegalArgumentException("Valor minimo para pelada paga e R$ 5,00")
                    }
                    UpdatePeladaRequest(valorPorJogador = valor)
                }
                "descricao" -> UpdatePeladaRequest(descricao = value)
                "dataHora" -> {
                    val dateTime = parseDateTime(value)
                        ?: throw IllegalArgumentException("Formato invalido. Use DD/MM HH:MM")
                    if (dateTime.isBefore(LocalDateTime.now())) {
                        throw IllegalArgumentException("A data deve ser no futuro")
                    }
                    UpdatePeladaRequest(dataHora = dateTime)
                }
                "chavePix" -> UpdatePeladaRequest(chavePix = value)
                else -> throw IllegalArgumentException("Campo desconhecido: $field")
            }

            peladaService.update(code, context.from, request)
            sessionManager.clear(context.from)
            ws.sendMessage(context.from, "\u2705 Campo *$field* atualizado!")

            notificationService.notifyParticipants(
                code,
                "\uD83D\uDD14 A pelada *$code* foi atualizada. Confira os novos detalhes!"
            )
        } catch (e: IllegalArgumentException) {
            ws.sendMessage(context.from, "\u26A0\uFE0F ${e.message}")
        } catch (e: Exception) {
            log.error("Error editing pelada {}: {}", code, e.message, e)
            ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
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
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

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
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

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
            log.error("Error cancelling pelada {}: {}", code, e.message, e)
            ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
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

    private fun solicitarSaque(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val walletBalance = pagamentoService.getWalletBalance(code)

        if (walletBalance <= BigDecimal.ZERO) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Voce nao tem saldo disponivel para saque nesta pelada.")
            ws.sendButtons(
                to = context.from,
                body = "Voltar:",
                buttons = listOf(Button(id = "/gerenciar financeiro $code", title = "Ver Financeiro"))
            )
            return
        }

        val organizerPhone = context.from.replace(Regex("[^0-9]"), "")
        val saqueMessage = "Solicito o saque da minha conta. ID: $organizerPhone Valor: $walletBalance"
        val deepLink = "https://wa.me/5581999536361?text=${java.net.URLEncoder.encode(saqueMessage, "UTF-8")}"

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCB3 *Solicitar Saque — $code*\n\n")
                append("\uD83D\uDCB0 *Saldo Disponivel:* R$ $walletBalance\n")
                append("_(ja descontadas as taxas da plataforma e gateway)_\n\n")
                append("Clique no link abaixo para solicitar o saque:\n")
                append(deepLink)
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Voltar:",
            buttons = listOf(
                Button(id = "/gerenciar financeiro $code", title = "Ver Financeiro"),
                Button(id = "/gerenciar pelada $code", title = "Menu Admin")
            )
        )
    }

    private fun parseDateTime(input: String): LocalDateTime? {
        return try {
            val clean = input.trim()
            if (clean.contains("/") && clean.count { it == '/' } == 1) {
                val parts = clean.split(" ", limit = 2)
                val datePart = parts[0]
                val timePart = parts.getOrElse(1) { "00:00" }
                val year = LocalDateTime.now().year
                LocalDateTime.parse("$datePart/$year $timePart", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            } else {
                LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun showConvidar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissao.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada.")
            return
        }

        val deepLink = "https://wa.me/5581983868651?text=$code"

        val shareMessage = buildString {
            append("Bora jogar! Entra na pelada comigo!\n\n")
            append("\uD83C\uDFC6 *${pelada.esporteLabel}*\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n")
            if (pelada.limiteJogadores == 0) {
                append("\uD83D\uDC65 Vagas ilimitadas\n")
            } else {
                append("\uD83D\uDC65 ${pelada.remainingSlots} vagas restantes\n")
            }
            if (pelada.valorPorJogador > BigDecimal.ZERO) {
                append("\uD83D\uDCB0 R$ ${pelada.valorPorJogador}\n")
            } else {
                append("\uD83D\uDCB0 Gratis\n")
            }
            append("\nPara participar, clique no link abaixo e envie a mensagem:\n$deepLink")
        }

        ws.sendMessage(
            context.from,
            "\uD83D\uDCE8 *Link de Convite*\n\n_Encaminhe a mensagem abaixo para seus amigos:_"
        )
        ws.sendMessage(context.from, shareMessage)

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar pelada $code", title = "Menu Admin"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
