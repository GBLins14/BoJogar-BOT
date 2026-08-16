package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.dto.request.CreatePeladaRequest
import com.bojogar.bot.enums.Esporte
import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.service.PlatformConfigService
import com.bojogar.bot.whatsapp.UxCopy
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class CriarCommand(
    private val sessionManager: SessionManager,
    private val peladaService: PeladaService,
    private val platformConfigService: PlatformConfigService,
    private val whatsAppProperties: WhatsAppProperties
) : BotCommand {

    override val name = "/criar"
    override val aliases = listOf("/nova", "/new")

    companion object {
        private val log = LoggerFactory.getLogger(CriarCommand::class.java)
        private val DATE_FORMATTER_FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        private val ZONE_BR = ZoneId.of("America/Sao_Paulo")
        private val DEFAULT_MIN_PRICE = BigDecimal(10)
        private val DEFAULT_MAX_PRICE = BigDecimal(100)
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        val sub = context.args.firstOrNull()

        when {
            sub == null -> startCreation(context, whatsappService)
            sub.startsWith("input_") -> handleInput(context, whatsappService, sub.removePrefix("input_"))
            sub == "confirmar" -> confirm(context, whatsappService)
            sub == "cancelar" -> cancelCreation(context, whatsappService)
            sub in Esporte.entries.map { it.name } -> handleSportSelected(context, whatsappService, sub)
            sub == "jogadores" -> handlePlayersSelected(context, whatsappService)
            else -> startCreation(context, whatsappService)
        }
    }

    // ── Passo 1: Esporte ────────────────────────────────────

    private fun startCreation(context: CommandContext, ws: WhatsAppService) {
        log.info("Iniciando criação de pelada para {} ({})", context.senderName, context.from)
        sessionManager.startCreatingPelada(context.from)

        ws.sendButtons(
            to = context.from,
            body = "*(1/6)* Bora criar sua pelada! \u26BD\n\nQual o esporte?",
            buttons = Esporte.entries.map { Button(id = "/criar ${it.name}", title = it.display) }
        )
    }

    // ── Passo 2: Nome/Descrição ──────────────────────────────

    private fun handleSportSelected(context: CommandContext, ws: WhatsAppService, sport: String) {
        val esporte = runCatching { Esporte.valueOf(sport) }.getOrNull()
        log.info("Esporte selecionado: {} por {}", sport, context.from)

        if (sessionManager.getSession(context.from) == null) {
            sessionManager.startCreatingPelada(context.from)
        }
        sessionManager.updateSession(context.from, "esporte", sport, "descricao")

        ws.sendButtons(
            to = context.from,
            body = "*(2/6)* ${esporte?.display ?: sport} selecionado!\n\nDê um nome para sua pelada _(opcional)_\n_Ex: Pelada de quinta, Rachão do Boa Vista_",
            buttons = listOf(
                Button(id = "/criar input_descricao _skip_", title = "Pular"),
                Button(id = "/criar cancelar", title = "\u274C Cancelar")
            )
        )
    }

    // ── Passos 3-6: Local, Data, Jogadores, Valor ─────────────

    private fun handleInput(context: CommandContext, ws: WhatsAppService, field: String) {
        val value = context.args.drop(1).joinToString(" ")
        log.info("Input campo=[{}] de {}", field, context.from)

        val session = sessionManager.getSession(context.from) ?: run {
            startCreation(context, ws)
            return
        }

        when (field) {
            "descricao" -> {
                val desc = if (value == "_skip_" || value.isBlank()) "" else value
                sessionManager.updateSession(context.from, "descricao", desc, "local")
                val confirmMsg = if (desc.isNotBlank()) "\uD83D\uDCDD *$desc*\n\n" else ""
                ws.sendButtons(
                    to = context.from,
                    body = "*(3/6)* ${confirmMsg}Onde vai ser a pelada?\n_Ex: Arena Beach \u2014 Boa Viagem_",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
                )
            }

            "local" -> {
                if (value.length < 5) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F O local precisa ter pelo menos 5 caracteres.\n_Ex: Arena Beach \u2014 Boa Viagem_")
                    return
                }
                sessionManager.updateSession(context.from, "local", value, "dataHora")
                ws.sendButtons(
                    to = context.from,
                    body = "*(4/6)* \uD83D\uDCCD $value\n\nQuando vai rolar?\n_Digite no formato DD/MM HH:MM_\n_Ex: 15/08 19:00_",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
                )
            }

            "dataHora" -> {
                val dateTime = parseDateTime(value)
                if (dateTime == null) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Não entendi a data.\n\nUse o formato *DD/MM HH:MM*\n_Ex: 15/08 19:00_")
                    return
                }
                if (dateTime.isBefore(LocalDateTime.now(ZONE_BR))) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Essa data já passou. Digite uma data futura.")
                    return
                }
                sessionManager.updateSession(context.from, "dataHora", dateTime.toString(), "maxPlayers")

                ws.sendList(
                    to = context.from,
                    body = "*(5/6)* \uD83D\uDCC5 ${UxCopy.formatDate(dateTime)}\n\nQuantas pessoas podem jogar?",
                    buttonLabel = "Escolher",
                    sections = listOf(
                        ListSection(
                            title = "Vagas",
                            rows = listOf(
                                ListRow(id = "/criar jogadores 6", title = "6 jogadores"),
                                ListRow(id = "/criar jogadores 8", title = "8 jogadores"),
                                ListRow(id = "/criar jogadores 10", title = "10 jogadores"),
                                ListRow(id = "/criar jogadores 12", title = "12 jogadores"),
                                ListRow(id = "/criar jogadores 14", title = "14 jogadores"),
                                ListRow(id = "/criar jogadores 16", title = "16 jogadores"),
                                ListRow(id = "/criar jogadores 20", title = "20 jogadores"),
                                ListRow(id = "/criar jogadores 0", title = "Sem limite"),
                                ListRow(id = "/criar jogadores custom", title = "Outro", description = "Digitar quantidade")
                            )
                        )
                    )
                )
            }

            "maxPlayers" -> {
                val max = value.toIntOrNull()
                if (max == null || (max != 0 && max < 2)) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Mínimo de 2 jogadores. Tente novamente.")
                    return
                }
                sessionManager.updateSession(context.from, "maxPlayers", max.toString(), "price")
                askPrice(context, ws)
            }

            "price" -> {
                val price = value.replace(",", ".").toBigDecimalOrNull()
                if (price == null || price < BigDecimal.ZERO) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F Não entendi. Digite um número.\n_Ex: 25 ou 0 para gratuita_")
                    return
                }
                val minPrice = platformConfigService.getMinPrice()
                val maxPrice = platformConfigService.getMaxPrice()
                if (price > BigDecimal.ZERO && price < minPrice) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F O mínimo é *R\$ $minPrice*.\nDigite *0* para gratuita ou um valor a partir de R\$ $minPrice.")
                    return
                }
                if (price > maxPrice) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F O máximo é *R\$ $maxPrice* por jogador.")
                    return
                }
                if (price > BigDecimal.ZERO) {
                    sessionManager.updateSession(context.from, "price", price.toString(), "pixKey")
                    ws.sendButtons(
                        to = context.from,
                        body = "\uD83D\uDCB0 R\$ $price por jogador.\n\nQual sua chave Pix para receber?\n_CPF, e-mail, telefone ou chave aleatória_",
                        buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
                    )
                } else {
                    sessionManager.updateSession(context.from, "price", price.toString(), null)
                    showSummary(context, ws)
                }
            }

            "pixKey" -> {
                if (value.isBlank()) {
                    ws.sendMessage(context.from, "\u26A0\uFE0F A chave Pix é obrigatória para peladas pagas.\nDigite sua chave Pix:")
                    return
                }
                sessionManager.updateSession(context.from, "pixKey", value, null)
                showSummary(context, ws)
            }
        }
    }

    private fun askPrice(context: CommandContext, ws: WhatsAppService) {
        val minPrice = platformConfigService.getMinPrice()
        val maxPrice = platformConfigService.getMaxPrice()
        ws.sendButtons(
            to = context.from,
            body = "*(6/6)* Vai cobrar dos jogadores?\n\nDigite o valor ou *0* para gratuita.\n_Mín. R\$ ${minPrice} · Máx. R\$ ${maxPrice}_",
            buttons = listOf(
                Button(id = "/criar input_price 0", title = "Gratuita"),
                Button(id = "/criar cancelar", title = "\u274C Cancelar")
            )
        )
    }

    // ── Seleção de jogadores via lista ──────────────────────

    private fun handlePlayersSelected(context: CommandContext, ws: WhatsAppService) {
        val max = context.args.getOrNull(1) ?: return

        if (max == "custom") {
            sessionManager.updateSession(context.from, "maxPlayers", "", "maxPlayers")
            ws.sendButtons(
                to = context.from,
                body = "*(5/6)* Quantos jogadores? _(mínimo 2)_",
                buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
            )
            return
        }

        sessionManager.updateSession(context.from, "maxPlayers", max, "price")
        askPrice(context, ws)
    }

    // ── Resumo e confirmação ────────────────────────────────

    private fun showSummary(context: CommandContext, ws: WhatsAppService) {
        val session = sessionManager.getSession(context.from) ?: return
        val fields = session.collectedFields

        val esporte = fields["esporte"]?.let { runCatching { Esporte.valueOf(it) }.getOrNull() }
        val dateTime = fields["dataHora"]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val price = fields["price"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val maxPlayers = fields["maxPlayers"]?.toIntOrNull() ?: 0

        val descricao = fields["descricao"]?.takeIf { it.isNotBlank() }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDCCB *Confira sua pelada:*\n\n")
                append("${esporte?.display ?: fields["esporte"]}\n")
                if (descricao != null) append("\uD83D\uDCDD $descricao\n")
                append("\uD83D\uDCCD ${fields["local"]}\n")
                append("\uD83D\uDCC5 ${dateTime?.let { UxCopy.formatDate(it) } ?: fields["dataHora"]}\n")
                append("\uD83D\uDC65 ${if (maxPlayers == 0) "Sem limite de jogadores" else "$maxPlayers jogadores"}\n")
                append("\uD83D\uDCB0 ${UxCopy.formatPrice(price)}")
                if (!fields["pixKey"].isNullOrBlank()) {
                    append("\n\uD83D\uDCF2 Pix: ${fields["pixKey"]}")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Tudo certo. Criar pelada?",
            buttons = listOf(
                Button(id = "/criar confirmar", title = "\u2705 Criar Pelada"),
                Button(id = "/criar cancelar", title = "\u274C Cancelar")
            )
        )
    }

    private fun confirm(context: CommandContext, ws: WhatsAppService) {
        log.info("Confirmando criação de pelada para {}", context.from)
        val session = sessionManager.getSession(context.from)
        if (session == null) {
            ws.sendButtons(
                to = context.from,
                body = "\u26A0\uFE0F A sessão expirou. Vamos recomeçar?",
                buttons = listOf(
                    Button(id = "/criar", title = "Criar Pelada"),
                    Button(id = "/start", title = "Menu")
                )
            )
            return
        }

        val fields = session.collectedFields

        try {
            val esporte = Esporte.valueOf(fields["esporte"] ?: throw IllegalArgumentException("Esporte obrigatório"))
            val dateTime = LocalDateTime.parse(fields["dataHora"] ?: throw IllegalArgumentException("Data obrigatória"))
            val maxPlayers = fields["maxPlayers"]?.toInt() ?: throw IllegalArgumentException("Limite obrigatório")
            val price = fields["price"]?.toBigDecimal() ?: BigDecimal.ZERO

            if (dateTime.isBefore(LocalDateTime.now(ZONE_BR))) {
                sessionManager.updateSession(context.from, "dataHora", "", "dataHora")
                ws.sendButtons(
                    to = context.from,
                    body = "\u26A0\uFE0F A data *${UxCopy.formatDate(dateTime)}* já passou.\n\nDigite uma nova data _(DD/MM HH:MM)_:",
                    buttons = listOf(Button(id = "/criar cancelar", title = "\u274C Cancelar"))
                )
                return
            }

            if (price > BigDecimal.ZERO && fields["pixKey"].isNullOrBlank()) {
                throw IllegalArgumentException("Chave Pix obrigatória para peladas pagas")
            }

            log.info("Criando pelada: esporte={}, local={}, data={}, jogadores={}, valor={}", esporte, fields["local"], dateTime, maxPlayers, price)
            val pelada = peladaService.create(
                phone = context.from,
                request = CreatePeladaRequest(
                    esporte = esporte.name,
                    descricao = fields["descricao"]?.takeIf { it.isNotBlank() },
                    dataHora = dateTime,
                    local = fields["local"] ?: throw IllegalArgumentException("Local obrigatório"),
                    limiteJogadores = maxPlayers,
                    valorPorJogador = price,
                    chavePix = fields["pixKey"]
                )
            )

            sessionManager.clear(context.from)
            log.info("Pelada criada! Código: {} por {}", pelada.codigo, context.from)

            val botPhone = whatsAppProperties.phoneNumber.replace(Regex("[^0-9]"), "")
            val deepLink = "https://wa.me/$botPhone?text=${pelada.codigo}"

            ws.sendMessage(
                context.from,
                buildString {
                    append("\u2705 *Pelada criada!*\n\n")
                    append("Código: *${pelada.codigo}*\n\n")
                    append("_Encaminhe a mensagem abaixo para seus amigos:_")
                }
            )

            ws.sendMessage(
                context.from,
                buildString {
                    append("Bora jogar! Entra na pelada comigo! \uD83D\uDCAA\n\n")
                    append("\uD83C\uDFC6 *${pelada.esporteLabel}*\n")
                    append("\uD83D\uDCCD ${pelada.local}\n")
                    append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n")
                    append("\uD83D\uDC65 ${UxCopy.formatRemaining(pelada.remainingSlots, pelada.limiteJogadores)}\n")
                    append("\uD83D\uDCB0 ${UxCopy.formatPrice(pelada.valorPorJogador)}\n")
                    append("\nPara participar, clique no link e envie a mensagem:\n$deepLink")
                }
            )

            ws.sendButtons(
                to = context.from,
                body = "O que fazer agora?",
                buttons = listOf(
                    Button(id = "/gerenciar convidar ${pelada.codigo}", title = "\uD83D\uDCE8 Convidar"),
                    Button(id = "/gerenciar pelada ${pelada.codigo}", title = "\uD83D\uDC51 Gerenciar"),
                    Button(id = "/start", title = "Menu")
                )
            )
        } catch (e: Exception) {
            log.error("Erro ao criar pelada para {}: {}", context.from, e.message, e)
            sessionManager.clear(context.from)
            ws.sendButtons(
                to = context.from,
                body = "\u274C Não foi possível criar a pelada. Tente novamente.",
                buttons = listOf(
                    Button(id = "/criar", title = "Tentar Novamente"),
                    Button(id = "/start", title = "Menu")
                )
            )
        }
    }

    private fun cancelCreation(context: CommandContext, ws: WhatsAppService) {
        log.info("Criação cancelada por {}", context.from)
        sessionManager.clear(context.from)
        ws.sendButtons(
            to = context.from,
            body = "Tudo bem, nada foi criado. \uD83D\uDC4D",
            buttons = listOf(
                Button(id = "/criar", title = "Criar Pelada"),
                Button(id = "/start", title = "Menu")
            )
        )
    }

    // ── Utilitários ─────────────────────────────────────────

    private fun parseDateTime(input: String): LocalDateTime? {
        return try {
            val clean = input.trim()
            if (clean.contains("/") && clean.count { it == '/' } == 1) {
                val parts = clean.split(" ", limit = 2)
                val datePart = parts[0]
                val timePart = parts.getOrElse(1) { "00:00" }
                val year = LocalDateTime.now(ZONE_BR).year
                LocalDateTime.parse("$datePart/$year $timePart", DATE_FORMATTER_FULL)
            } else {
                LocalDateTime.parse(clean, DATE_FORMATTER_FULL)
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
