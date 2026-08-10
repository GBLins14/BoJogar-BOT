package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPelada
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.UserService
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class ContaCommand(
    private val userService: UserService,
    private val participantService: ParticipantService
) : BotCommand {

    override val name = "/conta"
    override val aliases = listOf("/perfil")

    companion object {
        private val DATE_FMT_SHORT = DateTimeFormatter.ofPattern("EEE dd/MM - HH'h'", Locale("pt", "BR"))
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showConta(context, whatsappService)
            "peladas" -> showPeladas(context, whatsappService)
            "resetar" -> showResetar(context, whatsappService)
            "resetar_confirmar" -> confirmarReset(context, whatsappService)
            else -> showConta(context, whatsappService)
        }
    }

    private fun showConta(context: CommandContext, ws: WhatsAppService) {
        val user = userService.findByPhone(context.from)

        val allParticipations = participantService.getUserParticipations(context.from, activeOnly = false)
        val active = allParticipations.count {
            it.status in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST) &&
                it.pelada.status in listOf(StatusPelada.OPEN, StatusPelada.FULL) &&
                it.pelada.dataHora.isAfter(LocalDateTime.now())
        }
        val total = allParticipations.count {
            it.status == ParticipantStatus.CONFIRMED &&
                it.pelada.status in listOf(StatusPelada.FINISHED, StatusPelada.IN_PROGRESS)
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDC64 *Minha Conta*\n\n")
                append("\uD83D\uDCDD *Nome:* ${user?.name ?: context.senderName}\n")
                append("\uD83D\uDCF1 *Telefone:* ${PhoneUtils.formatPhoneDisplay(context.from)}\n")
                append("\uD83C\uDFD0 *Peladas ativas:* $active\n")
                append("\uD83D\uDCCA *Total participadas:* $total")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/conta peladas", title = "Minhas Peladas"),
                Button(id = "/conta resetar", title = "Resetar Conta"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showPeladas(context: CommandContext, ws: WhatsAppService) {
        val participations = participantService.getUserParticipations(context.from)
            .filter { it.pelada.dataHora.isAfter(LocalDateTime.now()) }

        if (participations.isEmpty()) {
            ws.sendMessage(context.from, "\uD83C\uDFD0 Voce nao esta inscrito em nenhuma pelada ativa.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/peladas proximas", title = "Ver Peladas"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        ws.sendList(
            to = context.from,
            header = "Minhas Peladas",
            body = "\uD83C\uDFD0 Peladas que voce esta participando:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Ativas",
                    rows = participations.take(10).map { p ->
                        ListRow(
                            id = "/minhas ver ${p.pelada.codigo}",
                            title = "${p.pelada.esporte.label} - ${p.pelada.local.take(20)}",
                            description = "${p.pelada.dataHora.format(DATE_FMT_SHORT)} | ${p.status.name}"
                        )
                    }
                )
            ),
            footer = "BoJogar"
        )
    }

    private fun showResetar(context: CommandContext, ws: WhatsAppService) {
        val active = participantService.getUserParticipations(context.from).size

        ws.sendButtons(
            to = context.from,
            header = "Resetar Conta",
            body = buildString {
                append("\u26A0\uFE0F *Atencao!* Esta acao ira:\n\n")
                append("\u274C Cancelar todas as suas inscricoes ($active ativas)\n")
                append("\u274C Sair de todas as peladas\n\n")
                append("*Essa acao nao pode ser desfeita.*")
            },
            buttons = listOf(
                Button(id = "/conta resetar_confirmar", title = "Sim, Resetar"),
                Button(id = "/conta", title = "Cancelar")
            )
        )
    }

    private fun confirmarReset(context: CommandContext, ws: WhatsAppService) {
        val participations = participantService.getUserParticipations(context.from)
        var cancelled = 0

        participations.forEach { p ->
            val result = participantService.leave(context.from, p.pelada.codigo)
            if (result is com.bojogar.bot.service.LeaveResult.Left) cancelled++
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\u2705 *Conta Resetada*\n\n")
                append("$cancelled inscricao(oes) cancelada(s).\n\n")
                append("Voce pode comecar de novo a qualquer momento!")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/peladas", title = "Ver Peladas"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
