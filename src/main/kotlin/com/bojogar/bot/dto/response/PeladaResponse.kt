package com.bojogar.bot.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

data class PeladaResponse(
    val id: UUID,
    val codigo: String,
    val esporte: String,
    val esporteLabel: String,
    val descricao: String?,
    val dataHora: LocalDateTime,
    val local: String,
    val limiteJogadores: Int,
    val valorPorJogador: BigDecimal,
    val chavePix: String?,
    val status: String,
    val createdByName: String,
    val createdByPhone: String,
    val confirmedCount: Long,
    val remainingSlots: Int,
    val waitlistCount: Long,
    val createdAt: Instant?
)
