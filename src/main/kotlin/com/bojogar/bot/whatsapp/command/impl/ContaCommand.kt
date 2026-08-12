package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.service.UserService
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

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
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        showConta(context, whatsappService)
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
        val played = allParticipations.count { p ->
            val pelada = peladaMap[p.peladaCodigo]
            p.status == "CONFIRMED" &&
                pelada != null &&
                pelada.status in listOf("FINISHED", "IN_PROGRESS")
        }

        val name = user?.name ?: context.senderName
        val phone = PhoneUtils.formatPhoneDisplay(context.from)
        val playedLabel = if (played == 1) "1 pelada" else "$played peladas"

        ws.sendButtons(
            to = context.from,
            body = buildString {
                append("\uD83D\uDC64 *Minha Conta*\n\n")
                append("\uD83D\uDCDD *Nome:* $name\n")
                append("\uD83D\uDCF1 *Telefone:* $phone\n")
                append("\u26BD *Peladas ativas:* $active\n")
                append("\uD83D\uDCCA *Jogou:* $playedLabel")
            },
            buttons = listOf(
                Button(id = "/minhas proximas", title = "\uD83D\uDCC5 Minhas Peladas"),
                Button(id = "/minhas historico", title = "\uD83D\uDCCA Hist\u00F3rico"),
                Button(id = "/start", title = "Menu")
            )
        )
    }
}
