package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.*
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
import java.time.Duration
import java.time.Instant

@Component
class PagarCommand(
    private val pagamentoService: PagamentoService,
    private val participantService: ParticipantService,
    private val peladaService: PeladaService,
    private val userService: UserService,
    private val sessionManager: SessionManager
) : BotCommand {

    override val name = "/pagar"
    override val aliases = listOf("/pay", "/pix")

    companion object {
        private val log = LoggerFactory.getLogger(PagarCommand::class.java)
        private val CPF_PATTERN = Regex("^\\d{11}$")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showPendingPayments(context, whatsappService)
            "gerar" -> gerarPix(context, whatsappService)
            "cpf_input" -> handleCpfInput(context, whatsappService)
            "ver" -> showPaymentDetails(context, whatsappService)
            else -> showPaymentDetails(context, whatsappService, sub)
        }
    }

    private fun showPendingPayments(context: CommandContext, ws: WhatsAppService) {
        log.info("Buscando pagamentos pendentes para {}", context.from)
        val pending = pagamentoService.getUserPendingPayments(context.from)

        if (pending.isEmpty()) {
            sendNoPendingPayments(context, ws)
            return
        }

        val participations = participantService.getUserParticipations(context.from)
        val peladasWithPending = participations.mapNotNull { p ->
            val payment = pagamentoService.findPendingPaymentForUser(context.from, p.peladaCodigo)
            if (payment != null) {
                val pelada = peladaService.findByCode(p.peladaCodigo)
                if (pelada != null) pelada to payment else null
            } else null
        }

        if (peladasWithPending.isEmpty()) {
            sendNoPendingPayments(context, ws)
            return
        }

        if (peladasWithPending.size == 1) {
            showPaymentDetails(context, ws, peladasWithPending.first().first.codigo)
            return
        }

        ws.sendList(
            to = context.from,
            header = "\uD83D\uDCB0 Pagamentos Pendentes",
            body = "Você tem *${peladasWithPending.size}* pagamento(s) pendente(s).\nSelecione para visualizar ou pagar:",
            buttonLabel = "Ver Pagamentos",
            sections = listOf(
                ListSection(
                    title = "Pendentes",
                    rows = peladasWithPending.take(10).map { (pelada, payment) ->
                        val hasValidPix = payment.pixCode != null && !isPixExpired(payment.pixGeneratedAt)
                        val icon = if (hasValidPix) "\uD83D\uDFE2" else "\uD83D\uDFE1"
                        ListRow(
                            id = "/pagar ver ${pelada.codigo}",
                            title = "$icon ${pelada.esporteLabel} — ${pelada.codigo}".take(24),
                            description = "R$ ${payment.valor} · ${pelada.local.take(20)}"
                        )
                    }
                )
            )
        )
    }

    private fun showPaymentDetails(context: CommandContext, ws: WhatsAppService, codeOverride: String? = null) {
        val code = codeOverride ?: context.args.getOrNull(1) ?: return showPendingPayments(context, ws)

        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)

        if (payment == null) {
            ws.sendButtons(
                to = context.from,
                body = "\u2705 Você não tem pagamento pendente para a pelada *$code*.\n\nMais alguma coisa?",
                buttons = listOf(
                    Button(id = "/minhas proximas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu")
                )
            )
            return
        }

        val pixExpired = payment.pixCode != null && isPixExpired(payment.pixGeneratedAt)

        if (payment.pixCode != null && !pixExpired) {
            val remaining = formatRemainingTime(payment.pixGeneratedAt)
            ws.sendMessage(
                context.from,
                buildString {
                    append("\uD83D\uDCB0 *Pagamento — ${pelada.codigo}*\n\n")
                    append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCB5 *Valor:* R$ ${payment.valor}\n")
                    append("\u23F1\uFE0F *Expira em:* $remaining\n\n")
                    append("\uD83D\uDCF2 *PIX Copia e Cola:*\n")
                    append("_Copie o código abaixo e cole no app do seu banco:_")
                }
            )
            ws.sendMessage(context.from, payment.pixCode!!)
            ws.sendButtons(
                to = context.from,
                body = "Após o pagamento, a confirmação é automática!",
                buttons = listOf(
                    Button(id = "/minhas proximas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu")
                )
            )
        } else if (pixExpired) {
            ws.sendMessage(
                context.from,
                buildString {
                    append("\u26A0\uFE0F *PIX Expirado — ${pelada.codigo}*\n\n")
                    append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCB5 *Valor:* R$ ${payment.valor}\n\n")
                    append("O código PIX anterior expirou.\nGere um novo para efetuar o pagamento.")
                }
            )
            ws.sendButtons(
                to = context.from,
                body = "Deseja gerar um novo PIX?",
                buttons = listOf(
                    Button(id = "/pagar gerar $code", title = "Gerar Novo PIX"),
                    Button(id = "/minhas proximas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu")
                )
            )
        } else {
            ws.sendMessage(
                context.from,
                buildString {
                    append("\uD83D\uDCB0 *Pagamento Pendente — ${pelada.codigo}*\n\n")
                    append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCB5 *Valor:* R$ ${payment.valor}\n\n")
                    append("Gere o código PIX para efetuar o pagamento.")
                }
            )
            ws.sendButtons(
                to = context.from,
                body = "Deseja gerar o PIX agora?",
                buttons = listOf(
                    Button(id = "/pagar gerar $code", title = "Gerar PIX"),
                    Button(id = "/minhas proximas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu")
                )
            )
        }
    }

    private fun gerarPix(context: CommandContext, ws: WhatsAppService) {
        log.info("Gerando PIX para {} na pelada {}", context.from, context.args.getOrNull(1))
        val code = context.args.getOrNull(1) ?: return showPendingPayments(context, ws)

        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Você não tem pagamento pendente para esta pelada.")
            return
        }

        if (payment.pixCode != null && !isPixExpired(payment.pixGeneratedAt)) {
            showPaymentDetails(context, ws, code)
            return
        }

        val cpf = userService.getUserCpf(context.from)
        if (cpf == null) {
            sessionManager.setCurrentPelada(context.from, code, ConversationState.ENTERING_CPF)
            ws.sendButtons(
                to = context.from,
                body = "\uD83D\uDCCB *CPF Necessário*\n\nPara gerar o PIX, precisamos do seu CPF.\n\nDigite os *11 dígitos* do seu CPF:",
                buttons = listOf(Button(id = "/start", title = "\u274C Cancelar"))
            )
            return
        }

        doGeneratePix(context, ws, code, cpf)
    }

    private fun handleCpfInput(context: CommandContext, ws: WhatsAppService) {
        log.info("CPF recebido de {} para pelada {}", context.from, context.args.getOrNull(1))
        val code = context.args.getOrNull(1) ?: return showPendingPayments(context, ws)
        val rawCpf = context.args.getOrNull(2) ?: return showPendingPayments(context, ws)

        val cpf = rawCpf.replace(Regex("[^0-9]"), "")

        if (!CPF_PATTERN.matches(cpf)) {
            ws.sendMessage(context.from, "\u26A0\uFE0F CPF inválido. Digite apenas os 11 dígitos numéricos:")
            return
        }

        userService.updateCpf(context.from, cpf)
        sessionManager.clear(context.from)

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Pagamento não encontrado ou já foi confirmado.")
            return
        }

        doGeneratePix(context, ws, code, cpf)
    }

    private fun doGeneratePix(
        context: CommandContext,
        ws: WhatsAppService,
        code: String,
        cpf: String
    ) {
        val user = userService.findByPhone(context.from)
        if (user == null) {
            ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Pagamento não encontrado ou já confirmado.")
            return
        }

        ws.sendMessage(context.from, "\u23F3 Gerando PIX...")

        val result = pagamentoService.generatePix(
            participantId = payment.participantId,
            userName = user.name,
            userPhone = context.from,
            userCpf = cpf,
            userEmail = userService.getUserEmail(context.from) ?: "${cpf}@bojogar.com"
        )

        when (result) {
            is PixGenerationResult.Success -> {
                log.info("PIX gerado com sucesso para {} na pelada {}", context.from, code)
                val pelada = peladaService.findByCode(code)
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *PIX Gerado!*\n\n")
                        if (pelada != null) {
                            append("\uD83C\uDFC6 ${pelada.esporteLabel} — ${pelada.codigo}\n")
                            append("\uD83D\uDCB5 *Valor:* R$ ${pelada.valorPorJogador}\n")
                        }
                        append("\u23F1\uFE0F *Expira em:* 30 minutos\n\n")
                        append("\uD83D\uDCF2 *PIX Copia e Cola:*\n")
                        append("_Copie o código abaixo e cole no app do seu banco:_")
                    }
                )
                ws.sendMessage(context.from, result.pixCode)
                ws.sendButtons(
                    to = context.from,
                    body = "Após o pagamento, a confirmação é automática!",
                    buttons = listOf(
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is PixGenerationResult.Error -> {
                log.error("Erro ao gerar PIX para {} na pelada {}: {}", context.from, code, result.message)
                ws.sendButtons(
                    to = context.from,
                    body = "\u274C Erro ao gerar o PIX. Tente novamente ou volte ao menu.",
                    buttons = listOf(
                        Button(id = "/pagar gerar $code", title = "Tentar Novamente"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
        }
    }

    private fun isPixExpired(pixGeneratedAt: Instant?): Boolean {
        if (pixGeneratedAt == null) return true
        return Instant.now().isAfter(pixGeneratedAt.plus(PagamentoService.PIX_EXPIRATION))
    }

    private fun formatRemainingTime(pixGeneratedAt: Instant?): String {
        if (pixGeneratedAt == null) return "—"
        val expiresAt = pixGeneratedAt.plus(PagamentoService.PIX_EXPIRATION)
        val remaining = Duration.between(Instant.now(), expiresAt)
        if (remaining.isNegative) return "Expirado"
        val minutes = remaining.toMinutes()
        return if (minutes >= 1) "$minutes min" else "menos de 1 min"
    }

    private fun sendNoPendingPayments(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            body = "\u2705 Você não tem pagamentos pendentes!\n\nMais alguma coisa?",
            buttons = listOf(
                Button(id = "/minhas proximas", title = "Minhas Peladas"),
                Button(id = "/start", title = "Menu")
            )
        )
    }
}
