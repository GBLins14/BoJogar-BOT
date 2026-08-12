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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StartCommand(
    private val sessionManager: SessionManager,
    private val authorizationService: AuthorizationService,
    private val pagamentoService: PagamentoService
) : BotCommand {

    override val name = "/start"
    override val aliases = listOf("/inicio", "/menu")

    companion object {
        private val log = LoggerFactory.getLogger(StartCommand::class.java)
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        log.info("Menu principal exibido para {} ({})", context.senderName, context.from)
        sessionManager.clear(context.from)

        val nome = context.senderName.ifBlank { "jogador" }
        val managed = authorizationService.getManagedPeladas(context.from)
        val pendingPayments = pagamentoService.getUserPendingPayments(context.from)

        val jogarRows = mutableListOf(
            ListRow(
                id = "/minhas proximas",
                title = "\uD83C\uDFDF\uFE0F Minhas Peladas",
                description = "Inscrições e próximas peladas"
            ),
            ListRow(
                id = "/criar",
                title = "\u2795 Criar Pelada",
                description = "Organize uma nova pelada"
            ),
            ListRow(
                id = "/entrar",
                title = "\uD83D\uDD11 Entrar com Código",
                description = "Participar de uma pelada"
            )
        )

        if (pendingPayments.isNotEmpty()) {
            jogarRows.add(
                ListRow(
                    id = "/pagar",
                    title = "\uD83D\uDCB0 Pagamentos (${pendingPayments.size})",
                    description = "${pendingPayments.size} pagamento(s) pendente(s)"
                )
            )
        }

        if (managed.isNotEmpty()) {
            jogarRows.add(
                ListRow(
                    id = "/gerenciar",
                    title = "\uD83D\uDC51 Gerenciar (${managed.size})",
                    description = "Administrar suas peladas"
                )
            )
        }

        val maisRows = listOf(
            ListRow(
                id = "/conta",
                title = "\uD83D\uDC64 Minha Conta",
                description = "Perfil e histórico"
            ),
            ListRow(
                id = "/ajuda",
                title = "\u2753 Como Funciona",
                description = "Aprenda a usar o BoJogar"
            ),
            ListRow(
                id = "/ajuda suporte",
                title = "\uD83D\uDCDE Suporte",
                description = "Fale com a gente"
            )
        )

        whatsappService.sendList(
            to = context.from,
            header = "\u26BD BoJogar",
            body = "E aí, $nome! \uD83D\uDC4B\n\nO que você quer fazer?",
            buttonLabel = "Abrir Menu",
            sections = listOf(
                ListSection(
                    title = "Jogar",
                    rows = jogarRows
                ),
                ListSection(
                    title = "Mais",
                    rows = maisRows
                )
            )
        )
    }
}
