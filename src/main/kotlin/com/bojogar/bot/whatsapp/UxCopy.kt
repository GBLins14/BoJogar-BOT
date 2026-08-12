package com.bojogar.bot.whatsapp

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Glossário UX do BoJogar — fonte única de toda a copy voltada ao usuário.
 *
 * Termos padronizados:
 *   pelada   → pelada (nunca "partida", "evento", "jogo")
 *   jogador  → jogador (nunca "participante", "usuário")
 *   organizador → organizador (nunca "owner", "admin")
 *   vaga     → vaga (nunca "slot")
 *   inscrição → inscrição (nunca "participação")
 */
object UxCopy {

    // ── Formatação ──────────────────────────────────────────
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
    private val DATE_FMT_SHORT = DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH'h'mm", Locale("pt", "BR"))
    private val DATE_FMT_COMPACT = DateTimeFormatter.ofPattern("dd/MM · HH:mm")

    fun formatDate(dt: LocalDateTime): String = dt.format(DATE_FMT)
    fun formatDateShort(dt: LocalDateTime): String = dt.format(DATE_FMT_SHORT)
    fun formatDateCompact(dt: LocalDateTime): String = dt.format(DATE_FMT_COMPACT)

    fun formatPrice(valor: BigDecimal): String =
        if (valor > BigDecimal.ZERO) "R$ $valor" else "Gratuita"

    fun formatSlots(confirmed: Long, limit: Int): String =
        if (limit == 0) "$confirmed jogadores" else "$confirmed/$limit jogadores"

    fun formatRemaining(remaining: Int, limit: Int): String = when {
        limit == 0 -> "Vagas ilimitadas"
        remaining <= 0 -> "Lotada"
        remaining == 1 -> "1 vaga restante"
        else -> "$remaining vagas restantes"
    }

    // ── Status humanos ──────────────────────────────────────
    fun statusPelada(status: String): String = when (status) {
        "OPEN" -> "Aberta"
        "FULL" -> "Lotada"
        "IN_PROGRESS" -> "Em andamento"
        "FINISHED" -> "Finalizada"
        "CANCELLED" -> "Cancelada"
        "DRAFT" -> "Rascunho"
        else -> status
    }

    fun statusJogador(status: String): String = when (status) {
        "CONFIRMED" -> "\u2705 Confirmado"
        "PENDING_PAYMENT" -> "\u23F3 Aguardando pagamento"
        "WAITLIST" -> "\uD83D\uDD52 Lista de espera"
        "CANCELLED" -> "\u274C Cancelado"
        "REMOVED" -> "\u274C Removido"
        else -> status
    }

    fun statusJogadorShort(status: String): String = when (status) {
        "CONFIRMED" -> "Confirmado"
        "PENDING_PAYMENT" -> "Aguardando pgto"
        "WAITLIST" -> "Espera"
        "CANCELLED" -> "Cancelado"
        "REMOVED" -> "Removido"
        else -> status
    }

    fun roleLabel(role: String): String = when (role) {
        "OWNER" -> "\uD83D\uDC51 Organizador"
        "ADMIN" -> "\u2699\uFE0F Admin"
        "PLAYER" -> "Jogador"
        else -> role
    }

    fun roleShort(role: String): String = when (role) {
        "OWNER" -> "\uD83D\uDC51"
        "ADMIN" -> "\u2699\uFE0F"
        else -> ""
    }

    // ── Status de pagamento ─────────────────────────────────
    fun statusPagamento(status: String): String = when (status) {
        "PENDENTE" -> "\u23F3 Pendente"
        "CONFIRMADO" -> "\u2705 Pago"
        "ESTORNADO" -> "\u21A9\uFE0F Estornado"
        else -> status
    }
}
