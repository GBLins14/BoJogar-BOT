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
        private val MIN_PRICE = BigDecimal(10)
        private val MAX_PRICE = BigDecimal(100)
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
        log.info("Listando peladas gerenciadas por {}", context.from)
        val managed = authorizationService.getManagedPeladas(context.from)

        if (managed.isEmpty()) {
            ws.sendMessage(context.from, "\u2699\uFE0F Você não gerencia nenhuma pelada no momento.")
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
            header = "\u2699\uFE0F Gerenciar Peladas",
            body = "Selecione a pelada que deseja gerenciar:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Suas Peladas",
                    rows = managed.take(10).mapNotNull { p ->
                        val pel = peladaService.findByCode(p.peladaCodigo)
                        if (pel != null) {
                            ListRow(
                                id = "/gerenciar pelada ${p.peladaCodigo}",
                                title = "${pel.esporteLabel} — ${p.peladaCodigo}",
                                description = "${pel.local.take(25)} · ${p.role}"
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
            ws.sendMessage(context.from, "\u274C Sem permissão para gerenciar esta pelada.")
            return
        }

        showPeladaAdminFor(context, ws, code)
    }

    private fun showPeladaAdminFor(context: CommandContext, ws: WhatsAppService, code: String) {
        log.info("Exibindo painel admin da pelada {} para {}", code, context.from)
        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\u2699\uFE0F *Painel Admin — ${pelada.codigo}*\n\n")
                append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                append("\uD83D\uDCCD ${pelada.local}\n")
                append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n")
                if (pelada.limiteJogadores == 0) {
                    append("\uD83D\uDC65 ${pelada.confirmedCount} confirmados · Sem limite\n")
                } else {
                    append("\uD83D\uDC65 ${pelada.confirmedCount}/${pelada.limiteJogadores} · ${pelada.remainingSlots} vagas restantes\n")
                }
                append("\uD83D\uDCCA Status: *${pelada.status}*")
            }
        )

        val sections = mutableListOf<ListSection>()
        sections.add(
            ListSection(
                title = "Gestão",
                rows = listOf(
                    ListRow(id = "/gerenciar participantes $code", title = "\uD83D\uDC65 Participantes", description = "${pelada.confirmedCount} confirmados"),
                    ListRow(id = "/gerenciar financeiro $code", title = "\uD83D\uDCB0 Financeiro", description = "Pagamentos e saldo"),
                    ListRow(id = "/gerenciar convidar $code", title = "\uD83D\uDCE8 Convidar Amigos", description = "Gerar link de convite")
                )
            )
        )
        sections.add(
            ListSection(
                title = "Configuração",
                rows = listOf(
                    ListRow(id = "/gerenciar editar $code", title = "\u270F\uFE0F Editar Pelada", description = "Local, data, valor..."),
                    ListRow(id = "/gerenciar cancelar $code", title = "\u274C Cancelar Pelada", description = "Cancelar e notificar todos")
                )
            )
        )

        ws.sendList(
            to = context.from,
            body = "Selecione uma opção:",
            buttonLabel = "Ver Opções",
            sections = sections,
            footer = "BoJogar"
        )
    }

    private fun showParticipantes(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        log.info("Listando participantes da pelada {} por {}", code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val participants = participantService.getActiveParticipants(code)

        if (participants.isEmpty()) {
            ws.sendMessage(context.from, "\uD83D\uDC65 Nenhum participante inscrito nesta pelada.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/gerenciar convidar $code", title = "Convidar Amigos"),
                    Button(id = "/gerenciar pelada $code", title = "Voltar")
                )
            )
            return
        }

        val confirmed = participants.filter { it.status == "CONFIRMED" }
        val pendingPayment = participants.filter { it.status == "PENDING_PAYMENT" }
        val waitlisted = participants.filter { it.status == "WAITLIST" }

        val sections = mutableListOf<ListSection>()

        if (confirmed.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\u2705 Confirmados (${confirmed.size})",
                    rows = confirmed.take(10).map { p ->
                        val roleLabel = if (p.role != "PLAYER") " [${p.role}]" else ""
                        ListRow(
                            id = "/gerenciar remover $code ${p.userPhone}",
                            title = "${p.displayName ?: p.userName}$roleLabel",
                            description = "${PhoneUtils.formatPhoneDisplay(p.userPhone)} · Pago"
                        )
                    }
                )
            )
        }

        if (pendingPayment.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\u23F3 Pendentes (${pendingPayment.size})",
                    rows = pendingPayment.take(10).map { p ->
                        val roleLabel = if (p.role != "PLAYER") " [${p.role}]" else ""
                        ListRow(
                            id = "/gerenciar remover $code ${p.userPhone}",
                            title = "${p.displayName ?: p.userName}$roleLabel",
                            description = "${PhoneUtils.formatPhoneDisplay(p.userPhone)} · Pendente"
                        )
                    }
                )
            )
        }

        if (waitlisted.isNotEmpty()) {
            sections.add(
                ListSection(
                    title = "\uD83D\uDD52 Lista de Espera (${waitlisted.size})",
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
            header = "\uD83D\uDC65 Participantes — $code",
            body = "${participants.size} participante(s) inscritos.\nSelecione um para gerenciar:",
            buttonLabel = "Ver Lista",
            sections = sections,
            footer = "BoJogar"
        )
    }

    private fun showRemover(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showParticipantes(context, ws)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.userPhone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante não encontrado.")
            return
        }

        ws.sendButtons(
            to = context.from,
            header = "Remover Participante",
            body = "\u26A0\uFE0F Deseja remover *${target.displayName ?: target.userName}* da pelada *$code*?\n\n_Essa ação não pode ser desfeita._",
            buttons = listOf(
                Button(id = "/gerenciar remover_sim $code $phone", title = "Sim, Remover"),
                Button(id = "/gerenciar participantes $code", title = "Não, Voltar")
            )
        )
    }

    private fun confirmarRemocao(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showParticipantes(context, ws)
        log.info("Removendo participante {} da pelada {} por {}", phone, code, context.from)

        when (val result = participantService.removeParticipant(context.from, phone, code)) {
            is RemoveResult.Removed -> {
                log.info("Participante {} removido da pelada {} com sucesso", phone, code)
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
                        "\uD83D\uDD04 *${result.promoted.displayName ?: result.promoted.userName}* foi promovido da lista de espera."
                    )
                }
            }
            is RemoveResult.NotFound -> ws.sendMessage(context.from, "\u26A0\uFE0F Participante não encontrado.")
            is RemoveResult.Unauthorized -> ws.sendMessage(context.from, "\u274C Sem permissão.")
            is RemoveResult.Error -> ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
        }

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/gerenciar participantes $code", title = "Ver Participantes"),
                Button(id = "/gerenciar pelada $code", title = "Painel Admin")
            )
        )
    }

    private fun confirmarPagamento(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        val phone = context.args.getOrNull(2) ?: return showManagedPeladas(context, ws)
        log.info("Confirmação manual de pagamento: {} na pelada {} por {}", phone, code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val participants = participantService.getParticipants(code)
        val target = participants.find { it.userPhone == phone }

        if (target == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Participante não encontrado.")
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
                Button(id = "/gerenciar pelada $code", title = "Painel Admin")
            )
        )
    }

    private fun showFinanceiro(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        log.info("Exibindo financeiro da pelada {} para {}", code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val pelada = peladaService.findByCode(code)
        if (pelada == null || pelada.valorPorJogador <= BigDecimal.ZERO) {
            ws.sendMessage(context.from, "\uD83D\uDCB0 Esta pelada é gratuita — sem movimentação financeira.")
            ws.sendButtons(
                to = context.from,
                body = "Voltar:",
                buttons = listOf(Button(id = "/gerenciar pelada $code", title = "Painel Admin"))
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
                append("\uD83D\uDCB5 *Total arrecadado:* R$ $totalCollected\n")
                append("\uD83D\uDCB3 *Saldo disponível:* R$ $walletBalance\n")
                append("_(já descontadas as taxas)_\n")

                if (payments.isNotEmpty()) {
                    append("\n")
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
                header = "Ações Financeiras",
                body = "Selecione uma opção:",
                buttonLabel = "Ver Opções",
                sections = listOf(
                    ListSection(
                        title = "Saque",
                        rows = listOf(
                            ListRow(
                                id = "/gerenciar saque $code",
                                title = "\uD83D\uDCB3 Solicitar Saque",
                                description = "Saldo: R$ $walletBalance"
                            )
                        )
                    ),
                    ListSection(
                        title = "Confirmar Pagamento",
                        rows = unpaid.take(9).map { p ->
                            ListRow(
                                id = "/gerenciar confirmar_pgto $code ${p.userPhone}",
                                title = p.displayName ?: p.userName,
                                description = "R$ ${pelada.valorPorJogador} · Pendente"
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
                    Button(id = "/gerenciar pelada $code", title = "Painel Admin")
                )
            )
        }
    }

    private fun showEditar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        log.info("Exibindo opções de edição da pelada {} para {}", code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\u270F\uFE0F *Editar Pelada — $code*\n\n")
                append("1\uFE0F\u20E3 *Local:* ${pelada.local}\n")
                append("2\uFE0F\u20E3 *Data:* ${pelada.dataHora.format(DATE_FMT)}\n")
                val limiteDisplay = if (pelada.limiteJogadores == 0) "Sem limite" else "${pelada.limiteJogadores} jogadores"
                append("3\uFE0F\u20E3 *Limite:* $limiteDisplay\n")
                append("4\uFE0F\u20E3 *Valor:* R$ ${pelada.valorPorJogador}\n")
                append("5\uFE0F\u20E3 *Descrição:* ${pelada.descricao ?: "—"}\n")
                append("6\uFE0F\u20E3 *Chave Pix:* ${pelada.chavePix ?: "—"}\n")
            }
        )

        ws.sendList(
            to = context.from,
            body = "Selecione o campo que deseja editar:",
            buttonLabel = "Ver Campos",
            sections = listOf(
                ListSection(
                    title = "Campos Editáveis",
                    rows = listOf(
                        ListRow(id = "/gerenciar editar_campo $code local", title = "\uD83D\uDCCD Local", description = pelada.local.take(30)),
                        ListRow(id = "/gerenciar editar_campo $code dataHora", title = "\uD83D\uDCC5 Data e Horário", description = pelada.dataHora.format(DATE_FMT)),
                        ListRow(id = "/gerenciar editar_campo $code limite", title = "\uD83D\uDC65 Limite de Jogadores", description = if (pelada.limiteJogadores == 0) "Sem limite" else "${pelada.limiteJogadores} jogadores"),
                        ListRow(id = "/gerenciar editar_campo $code valor", title = "\uD83D\uDCB0 Valor por Jogador", description = "R$ ${pelada.valorPorJogador}"),
                        ListRow(id = "/gerenciar editar_campo $code descricao", title = "\uD83D\uDCDD Descrição", description = (pelada.descricao ?: "—").take(30)),
                        ListRow(id = "/gerenciar editar_campo $code chavePix", title = "\uD83D\uDCF2 Chave Pix", description = (pelada.chavePix ?: "—").take(30))
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
        log.info("Editando campo [{}] da pelada {} para \"{}\" por {}", field, code, value, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        if (value.isBlank()) {
            sessionManager.setCurrentPelada(context.from, code, ConversationState.EDITING_PELADA)
            sessionManager.updateSession(context.from, "field", field, field)

            val label = when (field) {
                "local" -> "novo local"
                "limite" -> "novo limite de jogadores _(ou 0 para sem limite)_"
                "valor" -> "novo valor por jogador _(mín. R$ 10 · máx. R$ 100)_"
                "descricao" -> "nova descrição"
                "dataHora" -> "nova data e horário _(DD/MM HH:MM)_"
                "chavePix" -> "nova chave Pix"
                else -> "novo valor"
            }
            ws.sendButtons(
                to = context.from,
                body = "\u270F\uFE0F Digite o $label:",
                buttons = listOf(Button(id = "/gerenciar pelada $code", title = "\u274C Cancelar"))
            )
            return
        }

        try {
            val request = when (field) {
                "local" -> UpdatePeladaRequest(local = value)
                "limite" -> {
                    val limite = value.toIntOrNull() ?: throw IllegalArgumentException("Número inválido")
                    if (limite != 0 && limite < 2) throw IllegalArgumentException("Mínimo de 2 jogadores (ou 0 para sem limite)")
                    UpdatePeladaRequest(limiteJogadores = limite)
                }
                "valor" -> {
                    val valor = value.replace(",", ".").toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("Valor inválido")
                    if (valor > BigDecimal.ZERO && valor < MIN_PRICE) {
                        throw IllegalArgumentException("O valor mínimo para pelada paga é R$ $MIN_PRICE")
                    }
                    if (valor > MAX_PRICE) {
                        throw IllegalArgumentException("O valor máximo por jogador é R$ $MAX_PRICE")
                    }
                    UpdatePeladaRequest(valorPorJogador = valor)
                }
                "descricao" -> UpdatePeladaRequest(descricao = value)
                "dataHora" -> {
                    val dateTime = parseDateTime(value)
                        ?: throw IllegalArgumentException("Formato inválido. Use DD/MM HH:MM")
                    if (dateTime.isBefore(LocalDateTime.now())) {
                        throw IllegalArgumentException("A data precisa ser no futuro")
                    }
                    UpdatePeladaRequest(dataHora = dateTime)
                }
                "chavePix" -> UpdatePeladaRequest(chavePix = value)
                else -> throw IllegalArgumentException("Campo desconhecido: $field")
            }

            peladaService.update(code, context.from, request)
            sessionManager.clear(context.from)
            log.info("Campo [{}] da pelada {} atualizado com sucesso", field, code)

            val fieldLabel = when (field) {
                "local" -> "Local"
                "limite" -> "Limite de jogadores"
                "valor" -> "Valor"
                "descricao" -> "Descrição"
                "dataHora" -> "Data e horário"
                "chavePix" -> "Chave Pix"
                else -> field
            }
            ws.sendMessage(context.from, "\u2705 *$fieldLabel* atualizado com sucesso!")

            notificationService.notifyParticipants(
                code,
                "\uD83D\uDD14 A pelada *$code* foi atualizada pelo organizador. Confira os novos detalhes!"
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
                Button(id = "/gerenciar pelada $code", title = "Painel Admin")
            )
        )
    }

    private fun showCancelar(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)

        if (!authorizationService.isOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Apenas o organizador pode cancelar a pelada.")
            return
        }

        val participants = participantService.getActiveParticipants(code)

        ws.sendButtons(
            to = context.from,
            header = "Cancelar Pelada",
            body = buildString {
                append("\u26A0\uFE0F *Tem certeza?*\n\n")
                append("Cancelar a pelada *$code* afetará *${participants.size}* participante(s).\n")
                append("Todos serão notificados.\n\n")
                append("_Esta ação não pode ser desfeita._")
            },
            buttons = listOf(
                Button(id = "/gerenciar cancelar_sim $code", title = "Sim, Cancelar"),
                Button(id = "/gerenciar pelada $code", title = "Não, Voltar")
            )
        )
    }

    private fun confirmarCancelamento(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showManagedPeladas(context, ws)
        log.info("Cancelamento de pelada {} solicitado por {}", code, context.from)

        if (!authorizationService.isOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Apenas o organizador pode cancelar a pelada.")
            return
        }

        try {
            val participants = participantService.getActiveParticipants(code)
            val pelada = peladaService.cancel(code, context.from)

            notificationService.notifyPeladaCancelled(pelada, participants)
            log.info("Pelada {} cancelada por {} — {} participantes notificados", code, context.from, participants.size)

            ws.sendMessage(
                context.from,
                "\u274C *Pelada Cancelada*\n\nA pelada *$code* foi cancelada e *${participants.size}* participante(s) foram notificados."
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
        log.info("Solicitação de saque da pelada {} por {}", code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val walletBalance = pagamentoService.getWalletBalance(code)

        if (walletBalance <= BigDecimal.ZERO) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Você não tem saldo disponível para saque nesta pelada.")
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
                append("\uD83D\uDCB0 *Saldo disponível:* R$ $walletBalance\n")
                append("_(já descontadas as taxas da plataforma)_\n\n")
                append("Clique no link abaixo para solicitar o saque:\n")
                append(deepLink)
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Voltar:",
            buttons = listOf(
                Button(id = "/gerenciar financeiro $code", title = "Ver Financeiro"),
                Button(id = "/gerenciar pelada $code", title = "Painel Admin")
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
        log.info("Gerando link de convite da pelada {} por {}", code, context.from)

        if (!authorizationService.isAdminOrOwner(context.from, code)) {
            ws.sendMessage(context.from, "\u274C Sem permissão.")
            return
        }

        val pelada = peladaService.findByCode(code) ?: run {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        val deepLink = "https://wa.me/5581983868651?text=$code"

        val shareMessage = buildString {
            append("Bora jogar! Entra na pelada comigo! \uD83D\uDCAA\n\n")
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
                append("\uD83D\uDCB0 Gratuita\n")
            }
            append("\nPara participar, clique no link e envie a mensagem:\n$deepLink")
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
                Button(id = "/gerenciar pelada $code", title = "Painel Admin"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
