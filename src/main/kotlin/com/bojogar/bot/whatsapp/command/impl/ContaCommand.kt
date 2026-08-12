package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.service.UserService
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class ContaCommand(
    private val userService: UserService,
    private val participantService: ParticipantService,
    private val peladaService: PeladaService
) : BotCommand {

    override val name = "/conta"
    override val aliases = listOf("/perfil")

    companion object {
        private val log = LoggerFactory.getLogger(ContaCommand::class.java)
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
        log.info("Exibindo conta de {}", context.from)
        val user = userService.findByPhone(context.from)

        val allParticipations = participantService.getUserParticipations(context.from, activeOnly = false)
        val peladaMap = allParticipations.map { it.peladaCodigo }.distinct()
            .mapNotNull { code -> peladaService.findByCode(code)?.let { code to it } }
            .toMap()

        val active = allParticipations.count { p ->
            val pelada = peladaMap[p.peladaCodigo]
            p.status in listOf("CONFIRMED", "WAITLIST") &&
                pelada != null &&
                pelada.status in listOf("OPEN", "FULL") &&
                pelada.dataHora.isAfter(LocalDateTime.now())
        }
        val total = allParticipations.count { p ->
            val pelada = peladaMap[p.peladaCodigo]
            p.status == "CONFIRMED" &&
                pelada != null &&
                pelada.status in listOf("FINISHED", "IN_PROGRESS")
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDC64 *Minha Conta*\n\n")
                append("\uD83D\uDCDD *Nome:* ${user?.name ?: context.senderName}\n")
                append("\uD83D\uDCF1 *Telefone:* ${PhoneUtils.formatPhoneDisplay(context.from)}\n")
                append("\uD83C\uDFC6 *Peladas ativas:* $active\n")
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
        val peladaMap = participations.map { it.peladaCodigo }.distinct()
            .mapNotNull { code -> peladaService.findByCode(code)?.let { code to it } }
            .toMap()

        val upcoming = participations.filter { p ->
            val pelada = peladaMap[p.peladaCodigo]
            pelada != null && pelada.dataHora.isAfter(LocalDateTime.now())
        }

        if (upcoming.isEmpty()) {
            ws.sendMessage(context.from, "\uD83C\uDFC6 Você não está inscrito em nenhuma pelada ativa.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/entrar", title = "Entrar com Código"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
            return
        }

        ws.sendList(
            to = context.from,
            header = "Minhas Peladas",
            body = "\uD83C\uDFC6 Peladas em que você está participando:",
            buttonLabel = "Ver Peladas",
            sections = listOf(
                ListSection(
                    title = "Ativas",
                    rows = upcoming.take(10).mapNotNull { p ->
                        val pel = peladaMap[p.peladaCodigo] ?: return@mapNotNull null
                        ListRow(
                            id = "/minhas ver ${p.peladaCodigo}",
                            title = "${pel.esporteLabel} — ${pel.local.take(20)}",
                            description = "${pel.dataHora.format(DATE_FMT_SHORT)} · ${p.status}"
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
                append("\u26A0\uFE0F *Atenção!* Esta ação irá:\n\n")
                append("\u274C Cancelar todas as suas inscrições ($active ativas)\n")
                append("\u274C Sair de todas as peladas\n\n")
                append("_Esta ação não pode ser desfeita._")
            },
            buttons = listOf(
                Button(id = "/conta resetar_confirmar", title = "Sim, Resetar"),
                Button(id = "/conta", title = "Cancelar")
            )
        )
    }

    private fun confirmarReset(context: CommandContext, ws: WhatsAppService) {
        log.info("Resetando conta de {}", context.from)
        val participations = participantService.getUserParticipations(context.from)
        var cancelled = 0

        participations.forEach { p ->
            val result = participantService.leave(context.from, p.peladaCodigo)
            if (result is com.bojogar.bot.service.LeaveResult.Left) cancelled++
        }

        log.info("Conta resetada: {} inscrições canceladas para {}", cancelled, context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\u2705 *Conta Resetada*\n\n")
                append("$cancelled inscrição(ões) cancelada(s).\n\n")
                append("Você pode começar de novo a qualquer momento!")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/entrar", title = "Entrar com Código"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
