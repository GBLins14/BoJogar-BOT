package com.bojogar.bot.whatsapp.command.impl

import com.bojogar.bot.config.AdminProperties
import com.bojogar.bot.config.SyncPayProperties
import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPagamento
import com.bojogar.bot.enums.StatusPelada
import com.bojogar.bot.repository.PagamentoRepository
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.service.PagamentoService
import com.bojogar.bot.service.PeladaService
import com.bojogar.bot.util.PhoneUtils
import com.bojogar.bot.whatsapp.UxCopy
import com.bojogar.bot.whatsapp.command.BotCommand
import com.bojogar.bot.whatsapp.command.CommandContext
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListRow
import com.bojogar.bot.whatsapp.model.ListSection
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId

@Component
@Transactional(readOnly = true)
class AdminSuperCommand(
    private val adminProperties: AdminProperties,
    private val userRepository: UserRepository,
    private val peladaRepository: PeladaRepository,
    private val participantRepository: PeladaParticipantRepository,
    private val pagamentoRepository: PagamentoRepository,
    private val peladaService: PeladaService,
    private val pagamentoService: PagamentoService,
    private val syncPayProperties: SyncPayProperties
) : BotCommand {

    override val name = "/adminsuper"

    companion object {
        private val log = LoggerFactory.getLogger(AdminSuperCommand::class.java)
        private val ZONE_BR = ZoneId.of("America/Sao_Paulo")
        private val GATEWAY_FEE_PER_PIX = BigDecimal("0.85")
    }

    override fun execute(context: CommandContext, whatsappService: WhatsAppService) {
        if (!isSuperAdmin(context.from)) {
            log.warn("Unauthorized /adminsuper attempt from {}", context.from)
            return
        }

        val sub = context.args.firstOrNull()

        when (sub) {
            null -> showDashboard(context, whatsappService)
            "peladas" -> showPeladas(context, whatsappService)
            "pelada" -> showPeladaDetail(context, whatsappService)
            "organizadores" -> showOrganizadores(context, whatsappService)
            "financeiro" -> showFinanceiro(context, whatsappService)
            "usuarios" -> showUsuarios(context, whatsappService)
            else -> showDashboard(context, whatsappService)
        }
    }

    private fun isSuperAdmin(phone: String): Boolean {
        if (adminProperties.phone.isBlank()) return false
        val normalized = PhoneUtils.normalizePhone(phone)
        val adminNormalized = PhoneUtils.normalizePhone(adminProperties.phone)
        return normalized == adminNormalized
    }

    private fun showDashboard(context: CommandContext, ws: WhatsAppService) {
        log.info("Admin super dashboard accessed by {}", context.from)

        val totalUsers = userRepository.count()
        val allPeladas = peladaRepository.findAll()
        val totalPeladas = allPeladas.size
        val activePeladas = allPeladas.count { it.status in listOf(StatusPelada.OPEN, StatusPelada.FULL) }
        val upcomingPeladas = allPeladas.count {
            it.status in listOf(StatusPelada.OPEN, StatusPelada.FULL) &&
                it.dataHora.isAfter(LocalDateTime.now(ZONE_BR))
        }

        val allPayments = pagamentoRepository.findAll()
        val totalPayments = allPayments.size
        val confirmedPayments = allPayments.count { it.status == StatusPagamento.CONFIRMADO }
        val pendingPayments = allPayments.count { it.status == StatusPagamento.PENDENTE }
        val totalRevenue = allPayments
            .filter { it.status == StatusPagamento.CONFIRMADO }
            .sumOf { it.valor }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *ADMIN SUPER \u2014 Dashboard*\n\n")
                append("\uD83D\uDC64 *Usuários:* $totalUsers\n\n")
                append("\u26BD *Peladas:*\n")
                append("  Total: $totalPeladas\n")
                append("  Ativas: $activePeladas\n")
                append("  Próximas: $upcomingPeladas\n\n")
                append("\uD83D\uDCB0 *Pagamentos:*\n")
                append("  Total: $totalPayments\n")
                append("  Confirmados: $confirmedPayments\n")
                append("  Pendentes: $pendingPayments\n")
                append("  Receita total: R$ $totalRevenue\n")
            }
        )

        ws.sendList(
            to = context.from,
            body = "Selecione uma opção:",
            buttonLabel = "Ver Opções",
            sections = listOf(
                ListSection(
                    title = "Painel Admin",
                    rows = listOf(
                        ListRow(id = "/adminsuper peladas", title = "\u26BD Peladas Ativas", description = "$activePeladas ativas"),
                        ListRow(id = "/adminsuper organizadores", title = "\uD83D\uDC51 Organizadores", description = "Perfis e saldos"),
                        ListRow(id = "/adminsuper financeiro", title = "\uD83D\uDCB0 Financeiro Geral", description = "R$ $totalRevenue arrecadados"),
                        ListRow(id = "/adminsuper usuarios", title = "\uD83D\uDC64 Usuários", description = "$totalUsers cadastrados")
                    )
                )
            )
        )
    }

    private fun showPeladas(context: CommandContext, ws: WhatsAppService) {
        log.info("Admin super listing peladas")
        val peladas = peladaRepository.findAll()
            .filter { it.status in listOf(StatusPelada.OPEN, StatusPelada.FULL) }
            .sortedBy { it.dataHora }

        if (peladas.isEmpty()) {
            ws.sendButtons(
                to = context.from,
                body = "\u26BD Nenhuma pelada ativa no momento.",
                buttons = listOf(Button(id = "/adminsuper", title = "Voltar"))
            )
            return
        }

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *Peladas Ativas (${peladas.size})*\n\n")
                peladas.take(15).forEachIndexed { i, p ->
                    val confirmed = participantRepository.countByPeladaIdAndStatus(p.id!!, ParticipantStatus.CONFIRMED)
                    val limite = if (p.limiteJogadores > 0) "/${p.limiteJogadores}" else ""
                    append("${i + 1}. *${p.codigo}* \u2014 ${p.esporte.label}\n")
                    append("   ${p.local.take(30)}\n")
                    append("   ${UxCopy.formatDate(p.dataHora)} \u00B7 ${confirmed}$limite jogadores\n")
                    append("   Org: ${p.createdBy.name} (${p.createdBy.phone})\n")
                    if (p.valorPorJogador > BigDecimal.ZERO) {
                        append("   Valor: R$ ${p.valorPorJogador}\n")
                    }
                    append("\n")
                }
            }
        )

        val sections = listOf(
            ListSection(
                title = "Peladas",
                rows = peladas.take(10).map { p ->
                    ListRow(
                        id = "/adminsuper pelada ${p.codigo}",
                        title = "${p.codigo} \u2014 ${p.esporte.label}",
                        description = "${p.createdBy.name} \u00B7 ${UxCopy.formatDateCompact(p.dataHora)}"
                    )
                }
            )
        )

        ws.sendList(
            to = context.from,
            body = "Ver detalhes de uma pelada:",
            buttonLabel = "Ver Peladas",
            sections = sections
        )
    }

    private fun showPeladaDetail(context: CommandContext, ws: WhatsAppService) {
        val code = context.args.getOrNull(1) ?: return showPeladas(context, ws)
        val pelada = peladaRepository.findByCodigo(code.uppercase())
        if (pelada == null) {
            ws.sendMessage(context.from, "\u26A0\uFE0F Pelada *$code* não encontrada.")
            return
        }

        val participants = participantRepository.findByPeladaId(pelada.id!!)
        val confirmed = participants.filter { it.status == ParticipantStatus.CONFIRMED }
        val pending = participants.filter { it.status == ParticipantStatus.PENDING_PAYMENT }
        val waitlist = participants.filter { it.status == ParticipantStatus.WAITLIST }

        val payments = pagamentoRepository.findByParticipantPeladaCodigo(code.uppercase())
        val paidCount = payments.count { it.status == StatusPagamento.CONFIRMADO }
        val totalCollected = payments.filter { it.status == StatusPagamento.CONFIRMADO }.sumOf { it.valor }
        val walletBalance = pagamentoService.getWalletBalance(code)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *Pelada $code \u2014 Detalhes*\n\n")
                append("\uD83C\uDFC6 ${pelada.esporte.label}\n")
                append("\uD83D\uDCCD ${pelada.local}\n")
                append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n")
                append("\uD83D\uDCCA Status: *${pelada.status}*\n")
                append("\uD83D\uDC51 Org: ${pelada.createdBy.name} (${pelada.createdBy.phone})\n\n")

                append("*Jogadores:*\n")
                append("  \u2705 Confirmados: ${confirmed.size}\n")
                append("  \u23F3 Pag. pendente: ${pending.size}\n")
                append("  \uD83D\uDD52 Lista espera: ${waitlist.size}\n\n")

                if (pelada.valorPorJogador > BigDecimal.ZERO) {
                    append("*Financeiro:*\n")
                    append("  Valor/jogador: R$ ${pelada.valorPorJogador}\n")
                    append("  Pagos: $paidCount\n")
                    append("  Arrecadado: R$ $totalCollected\n")
                    append("  Saldo org: R$ $walletBalance\n")
                    append("  Chave PIX: ${pelada.chavePix ?: "\u2014"}\n\n")
                }

                if (confirmed.isNotEmpty()) {
                    append("*Confirmados:*\n")
                    confirmed.forEachIndexed { i, p ->
                        val role = if (p.role == ParticipantRole.OWNER) " (Org)" else ""
                        append("  ${i + 1}. ${p.displayName ?: p.user.name}$role \u2014 ${p.user.phone}\n")
                    }
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Ações:",
            buttons = listOf(
                Button(id = "/adminsuper peladas", title = "Todas Peladas"),
                Button(id = "/adminsuper", title = "Dashboard")
            )
        )
    }

    private fun showOrganizadores(context: CommandContext, ws: WhatsAppService) {
        log.info("Admin super listing organizers")

        val owners = participantRepository.findAll()
            .filter { it.role == ParticipantRole.OWNER && it.status == ParticipantStatus.CONFIRMED }
            .distinctBy { it.user.id }

        if (owners.isEmpty()) {
            ws.sendButtons(
                to = context.from,
                body = "\uD83D\uDC51 Nenhum organizador encontrado.",
                buttons = listOf(Button(id = "/adminsuper", title = "Voltar"))
            )
            return
        }

        val taxaPercent = syncPayProperties.platformFeePercent

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *Organizadores (${owners.size})*\n")
                append("_Saldo já com taxa de ${taxaPercent}% descontada_\n\n")
                owners.take(20).forEachIndexed { i, owner ->
                    val peladasCriadas = peladaRepository.findByCreatedByPhone(owner.user.phone)
                    val ativas = peladasCriadas.count { it.status in listOf(StatusPelada.OPEN, StatusPelada.FULL) }
                    val totalPeladas = peladasCriadas.size

                    var saldoTotal = BigDecimal.ZERO
                    var arrecadado = BigDecimal.ZERO
                    peladasCriadas.forEach { pel ->
                        saldoTotal = saldoTotal.add(pagamentoService.getWalletBalance(pel.codigo))
                        val payments = pagamentoRepository.findByParticipantPeladaCodigo(pel.codigo)
                        arrecadado = arrecadado.add(
                            payments.filter { it.status == StatusPagamento.CONFIRMADO }.sumOf { it.valor }
                        )
                    }

                    append("${i + 1}. *${owner.user.name}*\n")
                    append("   \uD83D\uDCF1 ${owner.user.phone}\n")
                    append("   Peladas: $totalPeladas (${ativas} ativas)\n")
                    append("   Arrecadado: R$ $arrecadado\n")
                    append("   Saldo disponível: R$ $saldoTotal\n\n")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Ações:",
            buttons = listOf(Button(id = "/adminsuper", title = "Dashboard"))
        )
    }

    private fun showFinanceiro(context: CommandContext, ws: WhatsAppService) {
        log.info("Admin super financial overview")

        val allPayments = pagamentoRepository.findAll()
        val confirmed = allPayments.filter { it.status == StatusPagamento.CONFIRMADO }
        val pending = allPayments.filter { it.status == StatusPagamento.PENDENTE }
        val refunded = allPayments.filter { it.status == StatusPagamento.ESTORNADO }

        val totalArrecadado = confirmed.sumOf { it.valor }
        val totalPending = pending.sumOf { it.valor }
        val totalRefunded = refunded.sumOf { it.valor }
        val qtdPixConfirmados = confirmed.size

        // Saldo organizadores (já com taxa da plataforma descontada)
        val peladasComPagamento = confirmed.map { it.participant.pelada.codigo }.distinct()
        var saldoOrganizadores = BigDecimal.ZERO
        peladasComPagamento.forEach { code ->
            saldoOrganizadores = saldoOrganizadores.add(pagamentoService.getWalletBalance(code))
        }

        // Receita plataforma = taxa cobrada dos organizadores
        val taxaPlataformaPercent = syncPayProperties.platformFeePercent
        val receitaPlataforma = totalArrecadado
            .multiply(BigDecimal(taxaPlataformaPercent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

        // Custo gateway = R$ 0.85 por PIX confirmado
        val custoGateway = GATEWAY_FEE_PER_PIX.multiply(BigDecimal(qtdPixConfirmados))

        // Lucro líquido = receita plataforma - custo gateway
        val lucroLiquido = receitaPlataforma.subtract(custoGateway).max(BigDecimal.ZERO)

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *Financeiro Geral*\n\n")

                append("*Transações:*\n")
                append("  \u2705 Confirmados: $qtdPixConfirmados\n")
                append("  \u23F3 Pendentes: ${pending.size}\n")
                append("  \u21A9\uFE0F Estornados: ${refunded.size}\n\n")

                append("*Valores:*\n")
                append("  \uD83D\uDCB5 Total arrecadado: R$ $totalArrecadado\n")
                append("  \u23F3 Pendente: R$ $totalPending\n")
                append("  \u21A9\uFE0F Estornado: R$ $totalRefunded\n\n")

                append("*Distribuição:*\n")
                append("  \uD83D\uDC51 Saldo organizadores: R$ $saldoOrganizadores\n")
                append("  \uD83D\uDCB0 Receita plataforma ($taxaPlataformaPercent%): R$ $receitaPlataforma\n")
                append("  \uD83C\uDFE6 Custo gateway (R$ 0,85 × $qtdPixConfirmados): R$ $custoGateway\n\n")

                append("*\uD83D\uDCB2 Lucro líquido: R$ $lucroLiquido*\n")
                append("_Peladas com pagamento: ${peladasComPagamento.size}_")
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Ações:",
            buttons = listOf(Button(id = "/adminsuper", title = "Dashboard"))
        )
    }

    private fun showUsuarios(context: CommandContext, ws: WhatsAppService) {
        log.info("Admin super listing users")

        val users = userRepository.findAll()

        ws.sendMessage(
            context.from,
            buildString {
                append("\uD83D\uDD12 *Usuários Cadastrados (${users.size})*\n\n")
                users.take(30).forEachIndexed { i, u ->
                    append("${i + 1}. *${u.name}* \u2014 ${u.phone}\n")
                }
                if (users.size > 30) {
                    append("\n_... e mais ${users.size - 30} usuários_")
                }
            }
        )

        ws.sendButtons(
            to = context.from,
            body = "Ações:",
            buttons = listOf(Button(id = "/adminsuper", title = "Dashboard"))
        )
    }
}
