package com.bojogar.bot.dto.response

import com.bojogar.bot.enums.StatusPagamento
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PagamentoResponse(
    val id: UUID,
    val inscricaoId: UUID,
    val valor: BigDecimal,
    val status: StatusPagamento,
    val transactionId: String?,
    val criadoEm: Instant?,
    val atualizadoEm: Instant?
)
