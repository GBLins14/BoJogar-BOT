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
            "\uD83D\uDD11 *Entrar em uma Pelada*\n\nDigite o código da pelada (6 caracteres):"
        )
    }

    private fun showPeladaDetails(context: CommandContext, ws: WhatsAppService, code: String) {
        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada. Verifique o código e tente novamente.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/entrar", title = "Tentar Novamente"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83C\uDFC6 *${pelada.esporteLabel} — ${pelada.codigo}*\n\n")
                if (!pelada.descricao.isNullOrBlank()) append("\uD83D\uDCDD ${pelada.descricao}\n\n")
                append("\uD83D\uDCCD *Local:* ${pelada.local}\n")
                append("\uD83D\uDCC5 *Data:* ${pelada.dataHora.format(DATE_FMT)}\n")
                if (pelada.limiteJogadores == 0) {
                    append("\uD83D\uDC65 *Vagas:* Ilimitadas\n")
                } else {
                    append("\uD83D\uDC65 *Vagas:* ${pelada.remainingSlots}/${pelada.limiteJogadores} restantes\n")
                }
                if (pelada.valorPorJogador > java.math.BigDecimal.ZERO) {
                    append("\uD83D\uDCB0 *Valor:* R$ ${pelada.valorPorJogador}\n")
                } else {
                    append("\uD83D\uDCB0 *Valor:* Gratuita\n")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Deseja participar desta pelada?",
            buttons = listOf(
                Button(id = "/entrar ${pelada.codigo} confirmar", title = "Participar"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun confirmJoin(context: CommandContext, ws: WhatsAppService, code: String) {
        when (val result = participantService.join(context.from, code)) {
            is JoinResult.Confirmed -> {
                val pelada = result.pelada
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *Inscrição Confirmada!*\n\n")
                        append("Você está na pelada *${pelada.codigo}* — ${pelada.esporteLabel}\n\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n\n")
                        append("Te vemos lá! \uD83D\uDCAA")
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.PendingPayment -> {
                val pelada = result.pelada
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\uD83D\uDCB0 *Pagamento Necessário*\n\n")
                        append("Pelada *${pelada.codigo}* — ${pelada.esporteLabel}\n\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n\n")
                        append("\uD83D\uDCB5 *Valor:* R$ ${pelada.valorPorJogador}\n\n")
                        append("_Sua vaga só será garantida após o pagamento._")
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "Pague agora para garantir sua vaga!",
                    buttons = listOf(
                        Button(id = "/pagar gerar ${pelada.codigo}", title = "Pagar via PIX"),
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.Waitlisted -> {
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\uD83D\uDCCB *Lista de Espera*\n\n")
                        append("A pelada *$code* está lotada.\n")
                        append("Você foi adicionado na *posição #${result.position}*.\n\n")
                        append("_Você será notificado assim que uma vaga abrir!_")
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.AlreadyJoined -> {
                ws.sendMessage(context.from, "\u26A0\uFE0F Você já está inscrito nesta pelada!")
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/minhas ver $code", title = "Ver Inscrição"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.PeladaClosed -> {
                ws.sendMessage(context.from, "\u274C Esta pelada não está aberta para inscrições.")
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/entrar", title = "Tentar Outro Código"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
            is JoinResult.Error -> {
                log.error("Error joining pelada {}: {}", code, result.message)
                ws.sendMessage(context.from, "\u274C Ocorreu um erro. Tente novamente mais tarde.")
                ws.sendButtons(
                    to = context.from,
                    body = "O que deseja fazer?",
                    buttons = listOf(
                        Button(id = "/entrar $code confirmar", title = "Tentar Novamente"),
                        Button(id = "/start", title = "Menu Inicial")
                    )
                )
            }
        }
    }
}
