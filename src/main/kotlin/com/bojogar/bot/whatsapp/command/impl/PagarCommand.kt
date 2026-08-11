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
import java.math.BigDecimal

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
        val pending = pagamentoService.getUserPendingPayments(context.from)

        if (pending.isEmpty()) {
            ws.sendMessage(context.from, "\u2705 Voce nao tem pagamentos pendentes!")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/minhas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        // List peladas with pending payments using participant data
        val participations = participantService.getUserParticipations(context.from)
        val peladasWithPending = participations.mapNotNull { p ->
            val payment = pagamentoService.findPendingPaymentForUser(context.from, p.peladaCodigo)
            if (payment != null) {
                val pelada = peladaService.findByCode(p.peladaCodigo)
                if (pelada != null) pelada to payment else null
            } else null
        }

        if (peladasWithPending.isEmpty()) {
            ws.sendMessage(context.from, "\u2705 Voce nao tem pagamentos pendentes!")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/minhas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        if (peladasWithPending.size == 1) {
            showPaymentDetails(context, ws, peladasWithPending.first().first.codigo)
            return
        }

        ws.sendList(
            to = context.from,
            header = "Pagamentos Pendentes",
            body = "\uD83D\uDCB0 ${peladasWithPending.size} pagamento(s) pendente(s):",
            buttonLabel = "Ver Pagamentos",
            sections = listOf(
                ListSection(
                    title = "Pendentes",
                    rows = peladasWithPending.take(10).map { (pelada, payment) ->
                        val icon = if (payment.pixCode != null) "\uD83D\uDFE2" else "\uD83D\uDFE1"
                        ListRow(
                            id = "/pagar ver ${pelada.codigo}",
                            title = "$icon ${pelada.esporteLabel} — ${pelada.codigo}",
                            description = "R$ ${payment.valor} | ${pelada.local.take(20)}"
                        )
                    }
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun showPaymentDetails(context: CommandContext, ws: WhatsAppService, codeOverride: String? = null) {
        val code = codeOverride ?: context.args.getOrNull(1) ?: return showPendingPayments(context, ws)

        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)

        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Voce nao tem pagamento pendente para a pelada *$code*.")
            return
        }

        if (payment.pixCode != null) {
            // PIX already generated — show it
            ws.sendMessage(
                context.from,
                buildString {
                    append("\uD83D\uDCB0 *Pagamento — ${pelada.codigo}*\n\n")
                    append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCB5 *Valor:* R$ ${payment.valor}\n\n")
                    append("\uD83D\uDCF2 *PIX Copia e Cola:*\n")
                    append("_Copie o codigo abaixo e cole no seu app bancario:_")
                }
            )
            ws.sendMessage(context.from, payment.pixCode!!)
            ws.sendButtons(
                to = context.from,
                body = "Apos pagar, o pagamento sera confirmado automaticamente!",
                buttons = listOf(
                    Button(id = "/minhas", title = "Minhas Peladas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        } else {
            // PIX not generated — offer to generate
            ws.sendMessage(
                context.from,
                buildString {
                    append("\uD83D\uDCB0 *Pagamento Pendente — ${pelada.codigo}*\n\n")
                    append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCB5 *Valor:* R$ ${payment.valor}\n\n")
                    append("Gere o codigo PIX para pagar!")
                }
            )
            ws.sendButtons(
                to = context.from,
                body = "Deseja gerar o PIX?",
                buttons = listOf(
                    Button(id = "/pagar gerar $code", title = "Gerar PIX"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        }
    }

    private fun gerarPix(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showPendingPayments(context, ws)

        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Voce nao tem pagamento pendente para esta pelada.")
            return
        }

        // If PIX already exists, just show it
        if (payment.pixCode != null) {
            showPaymentDetails(context, ws, code)
            return
        }

        // Check if user has CPF
        val cpf = userService.getUserCpf(context.from)
        if (cpf == null) {
            // Ask for CPF
            sessionManager.setCurrentPelada(context.from, code, ConversationState.ENTERING_CPF)
            ws.sendMessage(
                context.from,
                "\uD83D\uDCCB *CPF Necessario*\n\nPara gerar o PIX, precisamos do seu CPF.\n\nDigite os 11 digitos do seu CPF:"
            )
            return
        }

        // Generate PIX
        doGeneratePix(context, ws, code, payment.participantId.toString(), cpf)
    }

    private fun handleCpfInput(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return
        val rawCpf = context.args.getOrNull(2) ?: return

        val cpf = rawCpf.replace(Regex("[^0-9]"), "")

        if (!CPF_PATTERN.matches(cpf)) {
            ws.sendMessage(context.from, "\u26A0\uFE0F CPF invalido. Digite apenas os 11 digitos numéricos:")
            return
        }

        // Save CPF
        userService.updateCpf(context.from, cpf)
        sessionManager.clear(context.from)

        // Now generate PIX
        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Pagamento nao encontrado ou ja foi confirmado.")
            return
        }

        doGeneratePix(context, ws, code, payment.participantId.toString(), cpf)
    }

    private fun doGeneratePix(
        context: CommandContext,
        ws: WhatsAppService,
        code: String,
        participantId: String,
        cpf: String
    ) {
        val user = userService.findByPhone(context.from)
        if (user == null) {
            ws.sendMessage(context.from, "\u274C Erro: usuario nao encontrado.")
            return
        }

        val payment = pagamentoService.findPendingPaymentForUser(context.from, code)
        if (payment == null) {
            ws.sendMessage(context.from, "\u2705 Pagamento nao encontrado ou ja confirmado.")
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
                val pelada = peladaService.findByCode(code)
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *PIX Gerado!*\n\n")
                        if (pelada != null) {
                            append("\uD83C\uDFC6 ${pelada.esporteLabel} — ${pelada.codigo}\n")
                            append("\uD83D\uDCB5 *Valor:* R$ ${pelada.valorPorJogador}\n\n")
                        }
                        append("\uD83D\uDCF2 *PIX Copia e Cola:*\n")
                        append("_Copie o codigo abaixo e cole no seu app bancario:_")
                    }
                )
                ws.sendMessage(context.from, result.pixCode)
                ws.sendButtons(
                    to = context.from,
                    body = "Apos pagar, o pagamento sera confirmado automaticamente!",
                    buttons = listOf(
                        Button(id = "/minhas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is PixGenerationResult.Error -> {
                val pelada = peladaService.findByCode(code)
                ws.sendMessage(context.from, "\u274C ${result.message}")
                if (pelada != null && !pelada.chavePix.isNullOrBlank()) {
                    ws.sendMessage(
                        context.from,
                        buildString {
                            append("Voce pode pagar manualmente:\n\n")
                            append("\uD83D\uDCF2 *Chave Pix:* ${pelada.chavePix}\n")
                            append("\uD83D\uDCB5 *Valor:* R$ ${pelada.valorPorJogador}\n\n")
                            append("_Envie o comprovante ao organizador._")
                        }
                    )
                }
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/pagar gerar $code", title = "Tentar Novamente"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
        }
    }
}
