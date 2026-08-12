package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.JoinResult
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.whatsapp.UxCopy
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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
        log.info("Solicitando código de pelada para {}", context.from)
        sessionManager.startEnteringCode(context.from)
        ws.sendButtons(
            to = context.from,
            body = "\uD83D\uDD11 *Entrar em uma Pelada*\n\nDigite o código da pelada (6 caracteres):",
            buttons = listOf(Button(id = "/start", title = "\u274C Cancelar"))
        )
    }

    private fun showPeladaDetails(context: CommandContext, ws: WhatsAppService, code: String) {
        log.info("Exibindo detalhes da pelada {} para {}", code, context.from)
        val pelada = peladaService.findByCode(code)
        if (pelada == null) {
            log.info("Pelada {} não encontrada", code)
            ws.sendButtons(
                to = context.from,
                body = "\u26A0\uFE0F Pelada *$code* não encontrada.\nVerifique o código e tente novamente.",
                buttons = listOf(
                    Button(id = "/entrar", title = "Tentar Novamente"),
                    Button(id = "/start", title = "Menu")
                )
            )
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83C\uDFC6 *${pelada.esporteLabel} \u2014 ${pelada.codigo}*\n\n")
                if (!pelada.descricao.isNullOrBlank()) append("\uD83D\uDCDD ${pelada.descricao}\n\n")
                append("\uD83D\uDCCD *Local:* ${pelada.local}\n")
                append("\uD83D\uDCC5 *Data:* ${UxCopy.formatDate(pelada.dataHora)}\n")
                append("\uD83D\uDC65 *Vagas:* ${UxCopy.formatRemaining(pelada.remainingSlots, pelada.limiteJogadores)}\n")
                append("\uD83D\uDCB0 *Valor:* ${UxCopy.formatPrice(pelada.valorPorJogador)}")
                if (!pelada.createdByName.isNullOrBlank()) {
                    append("\n\uD83D\uDC64 *Organizador:* ${pelada.createdByName}")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Deseja participar desta pelada?",
            buttons = listOf(
                Button(id = "/entrar ${pelada.codigo} confirmar", title = "Participar"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun confirmJoin(context: CommandContext, ws: WhatsAppService, code: String) {
        log.info("Tentando inscrever {} na pelada {}", context.from, code)
        when (val result = participantService.join(context.from, code)) {
            is JoinResult.Confirmed -> {
                log.info("Inscrição confirmada: {} na pelada {}", context.from, code)
                val pelada = result.pelada
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\u2705 *Inscrição Confirmada!*\n\n")
                        append("Você está na pelada *${pelada.codigo}* \u2014 ${pelada.esporteLabel}\n\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n\n")
                        append("Te vemos lá! \uD83D\uDCAA")
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "Sua vaga está garantida!",
                    buttons = listOf(
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is JoinResult.PendingPayment -> {
                log.info("Inscrição pendente de pagamento: {} na pelada {}", context.from, code)
                val pelada = result.pelada
                ws.sendMessage(
                    context.from,
                    buildString {
                        append("\uD83D\uDCB0 *Pagamento Necessário*\n\n")
                        append("Pelada *${pelada.codigo}* \u2014 ${pelada.esporteLabel}\n\n")
                        append("\uD83D\uDCCD ${pelada.local}\n")
                        append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n\n")
                        append("\uD83D\uDCB5 *Valor:* ${UxCopy.formatPrice(pelada.valorPorJogador)}\n\n")
                        append("_Sua vaga só será garantida após o pagamento._")
                    }
                )
                ws.sendButtons(
                    to = context.from,
                    body = "Pague agora para garantir sua vaga!",
                    buttons = listOf(
                        Button(id = "/pagar gerar ${pelada.codigo}", title = "Pagar via PIX"),
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is JoinResult.Waitlisted -> {
                log.info("Lista de espera: {} na posição #{} na pelada {}", context.from, result.position, code)
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
                    body = "Enquanto isso:",
                    buttons = listOf(
                        Button(id = "/minhas proximas", title = "Minhas Peladas"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is JoinResult.AlreadyJoined -> {
                log.info("Já inscrito: {} na pelada {}", context.from, code)
                ws.sendButtons(
                    to = context.from,
                    body = "\u26A0\uFE0F Você já está inscrito nesta pelada!",
                    buttons = listOf(
                        Button(id = "/minhas ver $code", title = "Ver Inscrição"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is JoinResult.PeladaClosed -> {
                log.info("Pelada {} fechada para inscrições, tentativa de {}", code, context.from)
                ws.sendButtons(
                    to = context.from,
                    body = "\u274C Esta pelada não está aberta para inscrições.",
                    buttons = listOf(
                        Button(id = "/entrar", title = "Tentar Outro Código"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
            is JoinResult.Error -> {
                log.error("Error joining pelada {}: {}", code, result.message)
                ws.sendButtons(
                    to = context.from,
                    body = "\u274C Ocorreu um erro. Tente novamente mais tarde.",
                    buttons = listOf(
                        Button(id = "/entrar $code confirmar", title = "Tentar Novamente"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
        }
    }
}
