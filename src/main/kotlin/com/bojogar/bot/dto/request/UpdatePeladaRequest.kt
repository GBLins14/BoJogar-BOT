package com.bojogar.bot.dto.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class UpdatePeladaRequest(
    val descricao: String? = null,
    val dataHora: LocalDateTime? = null,
    val local: String? = null,
    val limiteJogadores: Int? = null,
    val valorPorJogador: BigDecimal? = null,
    val chavePix: String? = null
)
