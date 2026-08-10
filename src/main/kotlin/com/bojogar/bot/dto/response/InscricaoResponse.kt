package com.bojogar.bot.dto.response

import com.bojogar.bot.enums.StatusInscricao
import java.time.Instant
import java.util.UUID

data class InscricaoResponse(
    val id: UUID,
    val peladaId: UUID,
    val jogadorId: UUID,
    val status: StatusInscricao,
    val posicaoListaEspera: Int?,
    val criadoEm: Instant?,
    val atualizadoEm: Instant?
)
