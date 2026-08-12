package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AjudaCommand : BotCommand {

    override val name = "/ajuda"
    override val aliases = listOf("/help", "/comofunciona")

    companion object {
        private val log = LoggerFactory.getLogger(AjudaCommand::class.java)
        private const val SUPPORT_PHONE = "5581999536361"
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when (sub) {
            "criar" -> showCriar(context, whatsappService)
            "entrar" -> showEntrar(context, whatsappService)
            "pagar" -> showPagar(context, whatsappService)
            "organizador" -> showOrganizador(context, whatsappService)
            "suporte" -> showSuporte(context, whatsappService)
            else -> showTopics(context, whatsappService)
        }
    }

    private fun showTopics(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo tópicos de ajuda para {}", context.from)

        ws.sendList(
            to = context.from,
            header = "❓ Como Funciona",
            body = "Escolha um tópico para saber mais.",
            buttonLabel = "Ver Tópicos",
            sections = listOf(
                ListSection(
                    title = "Tópicos de Ajuda",
                    rows = listOf(
                        ListRow(id = "/ajuda criar", title = "Criar Pelada", description = "Como organizar uma pelada"),
                        ListRow(id = "/ajuda entrar", title = "Entrar em uma Pelada", description = "Como participar"),
                        ListRow(id = "/ajuda pagar", title = "Pagamentos", description = "Como funciona o PIX"),
                        ListRow(id = "/ajuda organizador", title = "Para Organizadores", description = "Gerenciar jogadores e saldo"),
                        ListRow(id = "/ajuda suporte", title = "\uD83D\uDCDE Suporte", description = "Fale com a gente")
                    )
                )
            )
        )
    }

    private fun showCriar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Criar Pelada' para {}", context.from)

        ws.sendMessage(
            context.from,
            "*Como criar uma pelada*\n\n" +
                "Toque em *Criar Pelada* no menu. Você escolhe o esporte, local, data, número de jogadores e valor.\n\n" +
                "No final, recebe um código de 6 letras para compartilhar com os amigos."
        )

        sendNavigationButtons(context, ws)
    }

    private fun showEntrar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Entrar em Pelada' para {}", context.from)

        ws.sendMessage(
            context.from,
            "*Como entrar em uma pelada*\n\n" +
                "Recebeu um código? Basta enviar aqui no chat! Você vê os detalhes e confirma com um toque."
        )

        sendNavigationButtons(context, ws)
    }

    private fun showPagar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Pagamentos' para {}", context.from)

        ws.sendMessage(
            context.from,
            "*Pagamentos*\n\n" +
                "Se a pelada for paga, você recebe um código PIX. Copie, cole no app do banco e pague. A confirmação é automática!"
        )

        sendNavigationButtons(context, ws)
    }

    private fun showOrganizador(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Organizadores' para {}", context.from)

        ws.sendMessage(
            context.from,
            "*Para organizadores*\n\n" +
                "No painel de gerenciamento, você acompanha jogadores, confirma pagamentos, edita a pelada e solicita saque do saldo."
        )

        sendNavigationButtons(context, ws)
    }

    private fun showSuporte(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo link de suporte para {}", context.from)

        ws.sendMessage(
            context.from,
            "*Suporte BoJogar*\n\n" +
                "Precisa de ajuda? Fale com a gente:\n\n" +
                "https://wa.me/$SUPPORT_PHONE"
        )

        ws.sendButtons(
            to = context.from,
            body = "Mais alguma coisa?",
            buttons = listOf(
                Button(id = "/ajuda", title = "Como Funciona"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun sendNavigationButtons(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            body = "Quer saber mais sobre outro assunto?",
            buttons = listOf(
                Button(id = "/ajuda", title = "Ver Outro Tópico"),
                Button(id = "/start", title = "Menu")
            )
        )
    }
}
