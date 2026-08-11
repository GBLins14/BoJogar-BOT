package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.dto.request.CreatePeladaRequest
import com.bojogar.bot.enums.Esporte
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
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
        sessionManager.startCreatingPelada(context.from)
        selectSport(context, ws)
    }

    private fun selectSport(context: CommandContext, ws: WhatsAppService) {
        ws.sendButtons(
            to = context.from,
            header = "Criar Pelada",
            body = "\uD83C\uDFD0 Qual o esporte?",
            buttons = Esporte.entries.map { Button(id = "/criar ${it.name}", title = it.label) },
            footer = "BoJogar"
        )
    }

    private fun handleSportSelected(context: CommandContext, ws: WhatsAppService, sport: String) {
        sessionManager.updateSession(context.from, "esporte", sport, "descricao")
        ws.sendMessage(
            context.from,
            "\uD83D\uDCDD *Descricao da pelada:*\n\nDigite uma breve descricao (ex: Pelada de quarta na praia)"
        )
    }

    private fun handleInput(context: CommandContext, ws: WhatsAppService, field: String) {
        val value = context.args.drop(1).joinToString(" ")
        val session = sessionManager.getSession(context.from) ?: run {
            startCreation(context, ws)
            return
        }

        when (field) {
            "descricao" -> {
                sessionManager.updateSession(context.from, "descricao", value, "local")
                ws.sendMessage(
                    context.from,
                    "\uD83D\uDCCD *Local:*\n\nDigite o local da pelada (ex: Quadra Arena Beach - Boa Viagem)"
                )
            }
            "local" -> {
                sessionManager.updateSession(context.from, "local", value, "dataHora")
                ws.sendMessage(
                    context.from,
                    "\uD83D\uDCC5 *Data e Horario:*\n\nDigite no formato DD/MM HH:MM (ex: 15/08 19:00)"
                )
            }
            "dataHora" -> {
                val dateTime = parseDateTime(value)
                if (dateTime == null) {
                    ws.sendMessage(
                        context.from,
                        "\u26A0\uFE0F Formato invalido. Use DD/MM HH:MM (ex: 15/08 19:00)"
                    )
                    return
                }
                if (dateTime.isBefore(LocalDateTime.now())) {
                    ws.sendMessage(
                        context.from,
                        "\u26A0\uFE0F A data deve ser no futuro. Tente novamente."
                    )
                    return
                }
                sessionManager.updateSession(context.from, "dataHora", dateTime.toString(), "maxPlayers")
                ws.sendButtons(
                    to = context.from,
                    body = "\uD83D\uDC65 *Quantos jogadores?*",
                    buttons = listOf(
                        Button(id = "/criar jogadores 4", title = "4 jogadores"),
                        Button(id = "/criar jogadores 8", title = "8 jogadores"),
                        Button(id = "/criar jogadores 12", title = "12 jogadores")
                    )
                )
            }
            "maxPlayers" -> {
                val max = value.toIntOrNull()
                if (max == null || max < 2) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Numero invalido. Minimo 2 jogadores.")
                    return
                }
                sessionManager.updateSession(context.from, "maxPlayers", max.toString(), "price")
                ws.sendMessage(
                    context.from,
                    "\uD83D\uDCB0 *Valor por jogador:*\n\nDigite o valor (ex: 25) ou 0 para gratis"
                )
            }
            "price" -> {
                val price = value.replace(",", ".").toBigDecimalOrNull()
                if (price == null || price < BigDecimal.ZERO) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Valor invalido. Digite um numero (ex: 25 ou 0)")
                    return
                }
                sessionManager.updateSession(context.from, "price", price.toString(), null)
                if (price > BigDecimal.ZERO) {
                    sessionManager.updateSession(
                        context.from,
                        session.withField("price", price.toString(), "pixKey")
                    )
                    ws.sendMessage(
                        context.from,
                        "\uD83D\uDCF2 *Chave Pix:*\n\nDigite a chave Pix para receber pagamentos"
                    )
                } else {
                    showSummary(context, ws)
                }
            }
            "pixKey" -> {
                sessionManager.updateSession(context.from, "pixKey", value, null)
                showSummary(context, ws)
            }
        }
    }

    private fun handlePlayersSelected(context: CommandContext, ws: WhatsAppService) {
        val max = context.args.getOrNull(1)
        if (max != null) {
            sessionManager.updateSession(context.from, "maxPlayers", max, "price")
            ws.sendMessage(
                context.from,
                "\uD83D\uDCB0 *Valor por jogador:*\n\nDigite o valor (ex: 25) ou 0 para gratis"
            )
        }
    }

    private fun showSummary(context: CommandContext, ws: WhatsAppService) {
        val session = sessionManager.getSession(context.from) ?: return
        val fields = session.collectedFields

        val esporte = fields["esporte"]?.let { runCatching { Esporte.valueOf(it) }.getOrNull() }
        val dateTime = fields["dataHora"]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Resumo da Pelada*\n\n")
                append("\uD83C\uDFC6 *Esporte:* ${esporte?.label ?: fields["esporte"]}\n")
                append("\uD83D\uDCDD *Descricao:* ${fields["descricao"] ?: "-"}\n")
                append("\uD83D\uDCCD *Local:* ${fields["local"]}\n")
                append("\uD83D\uDCC5 *Data:* ${dateTime?.format(DATE_FORMATTER_FULL) ?: fields["dataHora"]}\n")
                append("\uD83D\uDC65 *Jogadores:* ${fields["maxPlayers"]}\n")
                append("\uD83D\uDCB0 *Valor:* R$ ${fields["price"]}\n")
                if (!fields["pixKey"].isNullOrBlank()) {
                    append("\uD83D\uDCF2 *Pix:* ${fields["pixKey"]}\n")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Confirma a criacao?",
            buttons = listOf(
                Button(id = "/criar confirmar", title = "Confirmar"),
                Button(id = "/criar cancelar", title = "Cancelar")
            )
        )
    }

    private fun confirm(context: CommandContext, ws: WhatsAppService) {
        val session = sessionManager.getSession(context.from)
        if (session == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Sessao expirada. Use /criar para iniciar novamente.")
            return
        }

        val fields = session.collectedFields

        try {
            val esporte = Esporte.valueOf(fields["esporte"] ?: throw IllegalArgumentException("Esporte obrigatorio"))
            val dateTime = LocalDateTime.parse(fields["dataHora"] ?: throw IllegalArgumentException("Data obrigatoria"))
            val maxPlayers = fields["maxPlayers"]?.toInt() ?: throw IllegalArgumentException("Limite obrigatorio")
            val price = fields["price"]?.toBigDecimal() ?: BigDecimal.ZERO

            val pelada = peladaService.create(
                phone = context.from,
                request = CreatePeladaRequest(
                    esporte = esporte.name,
                    descricao = fields["descricao"],
                    dataHora = dateTime,
                    local = fields["local"] ?: throw IllegalArgumentException("Local obrigatorio"),
                    limiteJogadores = maxPlayers,
                    valorPorJogador = price,
                    chavePix = fields["pixKey"]
                )
            )

            sessionManager.clear(context.from)

            ws.sendMessage(
                context.from,
                buildString {
                    append("\u2705 *Pelada Criada!*\n\n")
                    append("\uD83D\uDD11 *Codigo:* ${pelada.codigo}\n\n")
                    append("Compartilhe o codigo *${pelada.codigo}* com seus amigos para eles entrarem!\n\n")
                    append("_Basta enviar o codigo ${pelada.codigo} aqui no chat para participar._")
                }
            )

            ws.sendButtons(
                to = context.from,
                body = "O que deseja fazer?",
                buttons = listOf(
                    Button(id = "/gerenciar pelada ${pelada.codigo}", title = "Gerenciar"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        } catch (e: Exception) {
            log.error("Error creating pelada for {}: {}", context.from, e.message, e)
            sessionManager.clear(context.from)
            ws.sendMessage(context.from, "\u274C Erro ao criar pelada: ${e.message}")
            ws.sendButtons(
                to = context.from,
                body = "Tente novamente:",
                buttons = listOf(
                    Button(id = "/criar", title = "Tentar Novamente"),
                    Button(id = "/start", title = "Menu Inicial")
                )
            )
        }
    }

    private fun cancelCreation(context: CommandContext, ws: WhatsAppService) {
        sessionManager.clear(context.from)
        ws.sendMessage(context.from, "\u274C Criacao cancelada.")
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
                // DD/MM HH:MM — add current year
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
