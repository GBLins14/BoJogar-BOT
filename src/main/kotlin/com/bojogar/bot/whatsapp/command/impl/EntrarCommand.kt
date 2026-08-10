package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.JoinResult
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class EntrarCommand(
    private val peladaService: PeladaService,
    private val participantService: ParticipantService,
    private val sessionManager: SessionManager
) : BotCommand {

    override val name = "/entrar"
    override val aliases = listOf("/join", "/codigo")

    companion object {
        private val log = LoggerFactory.getLogger(EntrarCommand::class.java)
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val code = context.args.firstOrNull()
        val sub = context.args.getOrNull(1)

        when {
            code == null -> askForCode(context, whatsappService)
            sub == "confirmar" -> confirmJoin(context, whatsappService, code)
            else -> showPeladaDetails(context, whatsappService, code)
        }
    }

    private fun askForCode(context: CommandContext, ws: WhatsAppService) {
        sessionManager.startEnteringCode(context.from)
        ws.sendMessage(
            context.from,
            "\uD83D\uDD11 *Entrar em uma Pelada*\n\nDigite o codigo da pelada (6 caracteres):"
        )
    }

    private fun showPeladaDetails(context: CommandContext, ws: WhatsAppService, code: String) {
        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* nao encontrada. Verifique o codigo.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/entrar", title = "Tentar Outro Codigo"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        val remaining = peladaService.getRemainingSlots(pelada)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83C\uDFD0 *${pelada.esporte.label} — ${pelada.codigo}*\n\n")
                if (!pelada.descricao.isNullOrBlank()) append("\uD83D\uDCDD ${pelada.descricao}\n")
                append("\uD83D\uDCCD *Local:* ${pelada.local}\n")
                append("\uD83D\uDCC5 *Data:* ${pelada.dataHora.format(DATE_FMT)}\n")
                append("\uD83D\uDC65 *Vagas:* $remaining/${pelada.limiteJogadores} restantes\n")
                if (pelada.valorPorJogador > java.math.BigDecimal.ZERO) {
                    append("\uD83D\uDCB0 *Valor:* R$ ${pelada.valorPorJogador}\n")
                    if (!pelada.chavePix.isNullOrBlank()) {
                        append("\uD83D\uDCF2 *Pix:* ${pelada.chavePix}\n")
                    }
                } else {
                    append("\uD83D\uDCB0 *Valor:* Gratis\n")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Deseja participar?",
            buttons = listOf(
                Button(id = "/entrar ${pelada.codigo} confirmar", title = "Participar"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun confirmJoin(context: CommandContext, ws: WhatsAppService, code: String) {
        when (val result = participantService.join(context.from, code)) {
            is JoinResult.Confirmed -> {
                val pelada = peladaService.findByCode(code)!!
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *Inscricao Confirmada!*\n\n")
                        append("Pelada *${pelada.codigo}* — ${pelada.esporte.label}\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n")
                        if (pelada.valorPorJogador > java.math.BigDecimal.ZERO) {
                            append("\n\uD83D\uDCB0 Valor: R$ ${pelada.valorPorJogador}\n")
                            if (!pelada.chavePix.isNullOrBlank()) {
                                append("\uD83D\uDCF2 Pix: ${pelada.chavePix}\n")
                                append("\n_Envie o comprovante ao organizador para confirmar._")
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
                    buildString {
                        append("\uD83D\uDCCB *Lista de Espera*\n\n")
                        append("A pelada *$code* esta lotada.\n")
                        append("Voce foi adicionado na *posicao #${result.position}* da lista de espera.\n\n")
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
            is JoinResult.AlreadyJoined -> {
                ws.sendMessage(context.from, "\u26A0\uFE0F Voce ja esta inscrito nesta pelada!")
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/minhas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.PeladaClosed -> {
                ws.sendMessage(context.from, "\u274C Esta pelada nao esta aberta para inscricoes.")
            }
            is JoinResult.Error -> {
                ws.sendMessage(context.from, "\u274C ${result.message}")
            }
        }
    }
}
