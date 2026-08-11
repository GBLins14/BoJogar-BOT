package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.service.AuthorizationService
import com.bojogar.bot.service.PagamentoService
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.SessionManager
import org.springframework.stereotype.Component

@Component
class StartCommand(
    private val sessionManager: SessionManager,
    private val authorizationService: AuthorizationService,
    private val pagamentoService: PagamentoService
) : BotCommand {

    override val name = "/start"
    override val aliases = listOf("/inicio", "/menu")

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        sessionManager.clear(context.from)

        val nome = context.senderName.ifBlank { "jogador" }
        val managed = authorizationService.getManagedPeladas(context.from)
        val pendingPayments = pagamentoService.getUserPendingPayments(context.from)

        val rows = mutableListOf(
            ListRow(
                id = "/entrar",
                title = "\uD83D\uDD11 Entrar com Código",
                description = "Participar de uma pelada existente"
            ),
            ListRow(
                id = "/minhas proximas",
                title = "\uD83D\uDCC5 Minhas Peladas",
                description = "Ver inscrições e histórico"
            ),
            ListRow(
                id = "/criar",
                title = "\u2795 Criar Pelada",
                description = "Organizar uma nova pelada"
            )
        )

        if (pendingPayments.isNotEmpty()) {
            rows.add(
                ListRow(
                    id = "/pagar",
                    title = "\uD83D\uDCB0 Pagamentos Pendentes",
                    description = "${pendingPayments.size} pagamento(s) aguardando"
                )
            )
        }

        if (managed.isNotEmpty()) {
            rows.add(
                ListRow(
                    id = "/gerenciar",
                    title = "\u2699\uFE0F Gerenciar Peladas",
                    description = "${managed.size} pelada(s) sob sua gestão"
                )
            )
        }

        whatsappService.sendList(
            to = context.from,
            header = "\u26BD BoJogar",
            body = buildString {
                append("E aí, $nome! \uD83D\uDC4B\n\n")
                append("Sou o *BoJogar*, seu assistente para organizar peladas.\n\n")
                append("Crie, gerencie e participe de peladas de forma rápida e prática.")
            },
            buttonLabel = "Ver Opções",
            sections = listOf(
                ListSection(
                    title = "Menu Principal",
                    rows = rows
                )
            ),
            footer = "BoJogar | v2.0"
        )
    }
}
