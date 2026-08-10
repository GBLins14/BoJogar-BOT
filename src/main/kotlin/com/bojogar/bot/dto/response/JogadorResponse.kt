package com.bojogar.bot.dto.response

import java.time.Instant
import java.util.UUID

data class JogadorResponse(
    val id: UUID,
    val nome: String,
    val telefone: String,
    val criadoEm: Instant?,
    val atualizadoEm: Instant?
)
