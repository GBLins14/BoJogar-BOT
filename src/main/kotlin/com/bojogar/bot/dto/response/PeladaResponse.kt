package com.bojogar.bot.dto.response

import com.bojogar.bot.enums.Esporte
import com.bojogar.bot.enums.StatusPelada
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

data class PeladaResponse(
    val id: UUID,
    val codigo: String,
    val organizadorId: UUID,
    val esporte: Esporte,
    val descricao: String?,
    val dataHora: LocalDateTime,
    val local: String,
    val limiteJogadores: Int,
    val valorPorJogador: BigDecimal,
    val chavePix: String?,
    val status: StatusPelada,
    val criadoEm: Instant?,
    val atualizadoEm: Instant?
)
