package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
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
            "suporte" -> showSuporte(context, whatsappService)
            else -> showComoFunciona(context, whatsappService)
        }
    }

    private fun showComoFunciona(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo 'Como Funciona' para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("*Como funciona o BoJogar?*\n\n")
                append("O BoJogar e o seu assistente para organizar peladas pelo WhatsApp! ")
                append("Aqui voce cria, gerencia e participa de peladas de forma rapida e pratica.\n\n")

                append("*1. Criar uma Pelada*\n")
                append("Toque em *Criar Pelada* no menu principal. Voce vai escolher o esporte, ")
                append("definir local, data, numero de jogadores e valor por pessoa. ")
                append("No final, voce recebe um *codigo de 6 caracteres* para compartilhar com os amigos.\n\n")

                append("*2. Entrar em uma Pelada*\n")
                append("Recebeu um codigo? Basta enviar aqui no chat ou tocar em *Entrar com Codigo*. ")
                append("Voce ve os detalhes da pelada e confirma sua participacao.\n\n")

                append("*3. Pagamento via PIX*\n")
                append("Se a pelada for paga, voce recebe um codigo PIX Copia e Cola. ")
                append("Copie, cole no app do seu banco e pague. ")
                append("A confirmacao e *automatica* - assim que o pagamento cair, sua vaga e garantida!\n\n")

                append("*4. Lista de Espera*\n")
                append("Se a pelada estiver lotada, voce entra na lista de espera. ")
                append("Quando alguem sair, voce e promovido automaticamente e recebe uma notificacao.\n\n")

                append("*5. Gerenciar Peladas (Organizador)*\n")
                append("Se voce criou a pelada, tem acesso ao *Painel Admin* onde pode:\n")
                append("  - Ver e remover participantes\n")
                append("  - Acompanhar pagamentos e saldo\n")
                append("  - Editar local, data, valor\n")
                append("  - Convidar amigos com link\n")
                append("  - Solicitar saque do saldo\n\n")

                append("*6. Minha Conta*\n")
                append("Em *Minha Conta* voce ve suas peladas ativas, historico e pode resetar suas inscricoes.\n\n")

                append("Qualquer duvida, toque em *Suporte* para falar com a gente!")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Precisa de mais alguma coisa?",
            buttons = listOf(
                Button(id = "/ajuda suporte", title = "Falar com Suporte"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun showSuporte(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo link de suporte para {}", context.from)

        val deepLink = "https://wa.me/$SUPPORT_PHONE"

        ws.sendMessage(
            context.from,
            buildString {
                append("*Suporte BoJogar*\n\n")
                append("Precisa de ajuda? Fale diretamente com a nossa equipe!\n\n")
                append("Clique no link abaixo para iniciar uma conversa:\n")
                append(deepLink)
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Mais alguma coisa?",
            buttons = listOf(
                Button(id = "/ajuda", title = "Como Funciona"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }
}
