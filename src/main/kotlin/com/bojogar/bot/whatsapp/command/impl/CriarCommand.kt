package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.dto.request.CreatePeladaRequest
import com.bojogar.bot.enums.Esporte
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import com.bojogar.bot.whatsapp.session.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class CriarCommand(
    private val sessionManager: SessionManager,
    private val peladaService: PeladaService
) : BotCommand {

    override val name = "/criar"
    override val aliases = listOf("/nova", "/new")

    companion object {
        private val log = LoggerFactory.getLogger(CriarCommand::class.java)
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        private val DATE_FORMATTER_FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        private val MIN_PRICE = BigDecimal(10)
        private val MAX_PRICE = BigDecimal(100)
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when {
            sub == null -> startCreation(context, whatsappService)
            sub.startsWith("input_") -> handleInput(context, whatsappService, sub.removePrefix("input_"))
            sub == "esporte" -> selectSport(context, whatsappService)
            sub == "confirmar" -> confirm(context, whatsappService)
            sub == "cancelar" -> cancelCreation(context, whatsappService)
            sub in Esporte.entries.map { it.name } -> handleSportSelected(context, whatsappService, sub)
            sub == "jogadores" -> handlePlayersSelected(context, whatsappService)
            else -> startCreation(context, whatsappService)
        }
    }

    private fun startCreation(context: CommandContext, ws: WhatsAppService) {
        log.info("Iniciando criação de pelada para {} ({})", context.senderName, context.from)
        sessionManager.startCreatingPelada(context.from)
        selectSport(context, ws)
    }

    private fun selectSport(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "\u2795 Nova Pelada",
            body = "Qual o esporte da pelada?",
            buttons = Esporte.entries.map { Button(id = "/criar ${it.name}", title = it.label) },
            footer = "Passo 1 de 6"
        )
    }

    private fun handleSportSelected(context: CommandContext, ws: WhatsAppService, sport: String) {
        log.info("Esporte selecionado: {} por {}", sport, context.from)
        if (sessionManager.getSession(context.from) == null) {
            sessionManager.startCreatingPelada(context.from)
        }
        sessionManager.updateSession(context.from, "esporte", sport, "descricao")
        ws.sendButtons(
            to = context.from,
            body = "\uD83D\uDCDD *Descrição da pelada*\n\nDigite uma breve descrição.\n_Ex: Pelada de quarta na praia_",
            buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
            footer = "Passo 2 de 6"
        )
    }

    private fun handleInput(context: CommandContext, ws: WhatsAppService, field: String) {
        log.info("Input recebido campo=[{}] valor=\"{}\" de {}", field, context.args.drop(1).joinToString(" "), context.from)
        val value = context.args.drop(1).joinToString(" ")
        val session = sessionManager.getSession(context.from) ?: run {
            startCreation(context, ws)
            return
        }

        when (field) {
            "descricao" -> {
                sessionManager.updateSession(context.from, "descricao", value, "local")
                ws.sendButtons(
                    to = context.from,
                    body = "\uD83D\uDCCD *Local da pelada*\n\nDigite o endereço ou nome do local.\n_Ex: Quadra Arena Beach — Boa Viagem_",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
                    footer = "Passo 3 de 6"
                )
            }
            "local" -> {
                sessionManager.updateSession(context.from, "local", value, "dataHora")
                ws.sendButtons(
                    to = context.from,
                    body = "\uD83D\uDCC5 *Data e horário*\n\nDigite no formato *DD/MM HH:MM*\n_Ex: 15/08 19:00_",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
                    footer = "Passo 4 de 6"
                )
            }
            "dataHora" -> {
                val dateTime = parseDateTime(value)
                if (dateTime == null) {
                    ws.sendMessage(
                        context.from,
                        "\u26A0\uFE0F Formato inválido. Use *DD/MM HH:MM*\n_Ex: 15/08 19:00_"
                    )
                    return
                }
                if (dateTime.isBefore(LocalDateTime.now())) {
                    ws.sendMessage(
                        context.from,
                        "\u26A0\uFE0F A data precisa ser no futuro. Tente novamente."
                    )
                    return
                }
                sessionManager.updateSession(context.from, "dataHora", dateTime.toString(), "maxPlayers")
                ws.sendList(
                    to = context.from,
                    body = "\uD83D\uDC65 *Quantos jogadores?*\n\nEscolha o limite de vagas para a pelada.",
                    buttonLabel = "Escolher",
                    sections = listOf(
                        ListSection(
                            title = "Limite de Jogadores",
                            rows = listOf(
                                ListRow(id = "/criar jogadores 4", title = "4 jogadores"),
                                ListRow(id = "/criar jogadores 6", title = "6 jogadores"),
                                ListRow(id = "/criar jogadores 8", title = "8 jogadores"),
                                ListRow(id = "/criar jogadores 10", title = "10 jogadores"),
                                ListRow(id = "/criar jogadores 12", title = "12 jogadores"),
                                ListRow(id = "/criar jogadores 16", title = "16 jogadores"),
                                ListRow(id = "/criar jogadores 20", title = "20 jogadores"),
                                ListRow(id = "/criar jogadores 0", title = "Sem limite", description = "Sem restrição de vagas"),
                                ListRow(id = "/criar jogadores custom", title = "Personalizado", description = "Digitar quantidade")
                            )
                        )
                    ),
                    footer = "Passo 4 de 6"
                )
            }
            "maxPlayers" -> {
                val max = value.toIntOrNull()
                if (max == null || (max != 0 && max < 2)) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Número inválido. Mínimo de 2 jogadores.")
                    return
                }
                sessionManager.updateSession(context.from, "maxPlayers", max.toString(), "price")
                ws.sendButtons(
                    to = context.from,
                    body = "\uD83D\uDCB0 *Valor por jogador*\n\nDigite o valor em reais.\n_Ex: 25_\n\nDigite *0* para pelada gratuita.\n_Mínimo R$ 10 · Máximo R$ 100_",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
                    footer = "Passo 5 de 6"
                )
            }
            "price" -> {
                val price = value.replace(",", ".").toBigDecimalOrNull()
                if (price == null || price < BigDecimal.ZERO) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Valor inválido. Digite um número.\n_Ex: 25 ou 0 para gratuita_")
                    return
                }
                if (price > BigDecimal.ZERO && price < MIN_PRICE) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F O valor mínimo para pelada paga é *R$ $MIN_PRICE*.\nDigite *0* para gratuita ou um valor a partir de R$ $MIN_PRICE.")
                    return
                }
                if (price > MAX_PRICE) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F O valor máximo por jogador é *R$ $MAX_PRICE*.")
                    return
                }
                if (price > BigDecimal.ZERO) {
                    sessionManager.updateSession(context.from, "price", price.toString(), "pixKey")
                    ws.sendButtons(
                        to = context.from,
                        body = "\uD83D\uDCF2 *Chave Pix*\n\nDigite a chave Pix para receber os pagamentos.\n\n_Os valores serão repassados com taxa de 10% da plataforma._",
                        buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
                        footer = "Passo 6 de 6"
                    )
                } else {
                    sessionManager.updateSession(context.from, "price", price.toString(), null)
                    showSummary(context, ws)
                }
            }
            "pixKey" -> {
                if (value.isBlank()) {
                    ws.sendMessage(
                        context.from,
                        "\u26A0\uFE0F A chave Pix é obrigatória para peladas pagas.\nDigite sua chave Pix (CPF, e-mail, telefone ou chave aleatória):"
                    )
                    return
                }
                sessionManager.updateSession(context.from, "pixKey", value, null)
                showSummary(context, ws)
            }
        }
    }

    private fun handlePlayersSelected(context: CommandContext, ws: WhatsAppService) {
        val max = context.args.getOrNull(1) ?: return

        if (max == "custom") {
            sessionManager.updateSession(context.from, "maxPlayers", "", "maxPlayers")
            ws.sendButtons(
                to = context.from,
                body = "\uD83D\uDC65 *Quantidade personalizada*\n\nDigite o número de jogadores _(mínimo 2)_:",
                buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
            )
            return
        }

        sessionManager.updateSession(context.from, "maxPlayers", max, "price")
        ws.sendButtons(
            to = context.from,
            body = "\uD83D\uDCB0 *Valor por jogador*\n\nDigite o valor em reais.\n_Ex: 25_\n\nDigite *0* para pelada gratuita.\n_Mínimo R$ 10 · Máximo R$ 100_",
            buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar")),
            footer = "Passo 5 de 6"
        )
    }

    private fun showSummary(context: CommandContext, ws: WhatsAppService) {
        val session = sessionManager.getSession(context.from) ?: return
        val fields = session.collectedFields

        val esporte = fields["esporte"]?.let { runCatching { Esporte.valueOf(it) }.getOrNull() }
        val dateTime = fields["dataHora"]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val price = fields["price"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val maxPlayers = fields["maxPlayers"]

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Resumo da Pelada*\n\n")
                append("\uD83C\uDFC6 *Esporte:* ${esporte?.label ?: fields["esporte"]}\n")
                append("\uD83D\uDCDD *Descrição:* ${fields["descricao"] ?: "—"}\n")
                append("\uD83D\uDCCD *Local:* ${fields["local"]}\n")
                append("\uD83D\uDCC5 *Data:* ${dateTime?.format(DATE_FORMATTER_FULL) ?: fields["dataHora"]}\n")
                append("\uD83D\uDC65 *Jogadores:* ${if (maxPlayers == "0") "Sem limite" else maxPlayers}\n")
                append("\uD83D\uDCB0 *Valor:* ${if (price > BigDecimal.ZERO) "R$ $price" else "Gratuita"}\n")
                if (!fields["pixKey"].isNullOrBlank()) {
                    append("\uD83D\uDCF2 *Pix:* ${fields["pixKey"]}\n")
                }
                append("\n_Confira os dados e confirme a criação._")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Tudo certo?",
            buttons = listOf(
                Button(id = "/criar confirmar", title = "\u2705 Confirmar"),
                Button(id = "/criar cancelar", title = "\u274C Cancelar")
            )
        )
    }

    private fun confirm(context: CommandContext, ws: WhatsAppService) {
        log.info("Confirmando criação de pelada para {}", context.from)
        val session = sessionManager.getSession(context.from)
        if (session == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Sessão expirada. Inicie novamente com /criar.")
            return
        }

        val fields = session.collectedFields

        try {
            val esporte = Esporte.valueOf(fields["esporte"] ?: throw IllegalArgumentException("Esporte obrigatório"))
            val dateTime = LocalDateTime.parse(fields["dataHora"] ?: throw IllegalArgumentException("Data obrigatória"))
            val maxPlayers = fields["maxPlayers"]?.toInt() ?: throw IllegalArgumentException("Limite obrigatório")
            val price = fields["price"]?.toBigDecimal() ?: BigDecimal.ZERO
            val pixKey = fields["pixKey"]

            if (price > BigDecimal.ZERO && pixKey.isNullOrBlank()) {
                throw IllegalArgumentException("Chave Pix obrigatória para peladas pagas")
            }

            log.info("Criando pelada: esporte={}, local={}, data={}, jogadores={}, valor={}", esporte, fields["local"], dateTime, maxPlayers, price)
            val pelada = peladaService.create(
                phone = context.from,
                request = CreatePeladaRequest(
                    esporte = esporte.name,
                    descricao = fields["descricao"],
                    dataHora = dateTime,
                    local = fields["local"] ?: throw IllegalArgumentException("Local obrigatório"),
                    limiteJogadores = maxPlayers,
                    valorPorJogador = price,
                    chavePix = fields["pixKey"]
                )
            )

            sessionManager.clear(context.from)
            log.info("Pelada criada com sucesso! Código: {} por {}", pelada.codigo, context.from)

            ws.sendMessage(
                context.from,
                buildString {
                    append("\u2705 *Pelada Criada com Sucesso!*\n\n")
                    append("\uD83D\uDD11 Código: *${pelada.codigo}*\n\n")
                    append("Compartilhe o código *${pelada.codigo}* com seus amigos para que eles participem!\n\n")
                    append("_Basta enviar o código aqui no chat para entrar._")
                }
            )

            ws.sendButtons(
                to = context.from,
                body = "Próximos passos:",
                buttons = listOf(
                    Button(id = "/gerenciar convidar ${pelada.codigo}", title = "Convidar Amigos"),
                    Button(id = "/gerenciar pelada ${pelada.codigo}", title = "Gerenciar"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        } catch (e: Exception) {
            log.error("Error creating pelada for {}: {}", context.from, e.message, e)
            sessionManager.clear(context.from)
            ws.sendMessage(context.from, "\u274C Ocorreu um erro ao criar a pelada. Tente novamente.")
            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/criar", title = "Tentar Novamente"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        }
    }

    private fun cancelCreation(context: CommandContext, ws: WhatsAppService) {
        log.info("Criação de pelada cancelada por {}", context.from)
        sessionManager.clear(context.from)
        ws.sendMessage(context.from, "\u274C Criação cancelada.")
        ws.sendButtons(
            to = context.from,
            body = "O que deseja fazer?",
            buttons = listOf(
                Button(id = "/criar", title = "Criar Pelada"),
                Button(id = "/start", title = "Menu Inicial")
            )
        )
    }

    private fun parseDateTime(input: String): LocalDateTime? {
        return try {
            val clean = input.trim()
            if (clean.contains("/") && clean.count { it == '/' } == 1) {
                val parts = clean.split(" ", limit = 2)
                val datePart = parts[0]
                val timePart = parts.getOrElse(1) { "00:00" }
                val year = LocalDateTime.now().year
                LocalDateTime.parse("$datePart/$year $timePart", DATE_FORMATTER_FULL)
            } else {
                LocalDateTime.parse(clean, DATE_FORMATTER_FULL)
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
