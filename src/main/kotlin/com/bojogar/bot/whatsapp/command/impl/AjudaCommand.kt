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
            header = "\u2753 Como Funciona",
            body = "O BoJogar organiza peladas pelo WhatsApp \u2014 da criação ao pagamento.\n\nEscolha um tópico:",
            buttonLabel = "Ver Tópicos",
            sections = listOf(
                ListSection(
                    title = "Tópicos de Ajuda",
                    rows = listOf(
                        ListRow(id = "/ajuda criar", title = "\u2795 Criar Pelada", description = "Passo a passo para organizar"),
                        ListRow(id = "/ajuda entrar", title = "\uD83D\uDD11 Entrar com Código", description = "Como participar de uma pelada"),
                        ListRow(id = "/ajuda pagar", title = "\uD83D\uDCB0 Pagamentos via PIX", description = "Como pagar e receber"),
                        ListRow(id = "/ajuda organizador", title = "\uD83D\uDC51 Área do Organizador", description = "Tudo sobre gerenciar sua pelada"),
                        ListRow(id = "/ajuda suporte", title = "\uD83D\uDCDE Falar com Suporte", description = "Tire dúvidas com a equipe")
                    )
                )
            )
        )
    }

    private fun showCriar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Criar Pelada' para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\u2795 *Criar uma Pelada*\n\n")
                append("Montar a pelada leva menos de 1 minuto:\n\n")
                append("1\uFE0F\u20E3 Escolha o esporte _(futebol, futevôlei ou vôlei)_\n")
                append("2\uFE0F\u20E3 Dê um nome _(opcional)_\n")
                append("3\uFE0F\u20E3 Informe o local e a data\n")
                append("4\uFE0F\u20E3 Defina o número de vagas\n")
                append("5\uFE0F\u20E3 Defina o valor _(ou gratuita)_\n\n")
                append("\uD83C\uDF1F Pronto! Você recebe um *código de 6 letras* para mandar pros amigos.")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Quer criar uma agora?",
            buttons = listOf(
                Button(id = "/criar", title = "\u2795 Criar Pelada"),
                Button(id = "/ajuda", title = "Outros Tópicos"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showEntrar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Entrar em Pelada' para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD11 *Entrar em uma Pelada*\n\n")
                append("Recebeu um código do organizador? É só mandar aqui no chat!\n\n")
                append("\uD83D\uDCCB Você vê o local, data, vagas e valor\n")
                append("\u2705 Toca em *Participar* e tá dentro\n\n")
                append("Se a pelada estiver lotada, você entra na *lista de espera* e é avisado quando abrir vaga.")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Tem um código? Entre agora:",
            buttons = listOf(
                Button(id = "/entrar", title = "\uD83D\uDD11 Entrar com Código"),
                Button(id = "/ajuda", title = "Outros Tópicos"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showPagar(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Pagamentos' para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCB0 *Pagamentos via PIX*\n\n")
                append("Se a pelada for paga, funciona assim:\n\n")
                append("1\uFE0F\u20E3 Ao entrar, toque em *Pagar via PIX*\n")
                append("2\uFE0F\u20E3 Informe seu CPF _(só na primeira vez)_\n")
                append("3\uFE0F\u20E3 Copie o código PIX gerado\n")
                append("4\uFE0F\u20E3 Cole no app do seu banco e pague\n\n")
                append("\u26A1 A confirmação é *automática* \u2014 assim que o banco processar, sua vaga é garantida.\n\n")
                append("_O valor fica entre R\$ 10 e R\$ 100, definido pelo organizador._")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Tem pagamento pendente?",
            buttons = listOf(
                Button(id = "/pagar", title = "\uD83D\uDCB0 Ver Pagamentos"),
                Button(id = "/ajuda", title = "Outros Tópicos"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showOrganizador(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo ajuda 'Organizadores' para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDC51 *Área do Organizador*\n\n")
                append("Criou a pelada? Você tem acesso ao painel completo:\n\n")
                append("\uD83D\uDC65 *Jogadores* \u2014 veja quem confirmou, está na espera ou pendente\n")
                append("\uD83D\uDCB0 *Financeiro* \u2014 acompanhe pagamentos e confirme manualmente\n")
                append("\uD83D\uDCB3 *Saque* \u2014 solicite a retirada do saldo arrecadado\n")
                append("\uD83D\uDCE8 *Convite* \u2014 gere um link para compartilhar com os amigos\n")
                append("\u270F\uFE0F *Editar* \u2014 altere local, data, valor ou limite de vagas\n")
                append("\u274C *Cancelar* \u2014 cancele a pelada e notifique todos\n\n")
                append("_Tudo direto por aqui, sem sair do WhatsApp._")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Quer gerenciar suas peladas?",
            buttons = listOf(
                Button(id = "/gerenciar", title = "\uD83D\uDC51 Gerenciar"),
                Button(id = "/ajuda", title = "Outros Tópicos"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    private fun showSuporte(context: CommandContext, ws: WhatsAppService) {
        log.info("Exibindo link de suporte para {}", context.from)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCDE *Suporte BoJogar*\n\n")
                append("Tá com algum problema ou tem uma sugestão? A gente responde rápido!\n\n")
                append("Clique no link abaixo para falar diretamente com o suporte:\n")
                append("https://wa.me/$SUPPORT_PHONE")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Mais alguma coisa?",
            buttons = listOf(
                Button(id = "/ajuda", title = "Outros Tópicos"),
                Button(id = "/start", title = "Menu")
            )
        )
    }
}
