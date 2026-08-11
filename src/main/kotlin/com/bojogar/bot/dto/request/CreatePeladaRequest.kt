package com.bojogar.bot.dto.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreatePeladaRequest(
    @field:NotBlank
    val esporte: String,

    val descricao: String? = null,

    @field:NotNull
    val dataHora: LocalDateTime,

    @field:NotBlank
    val local: String,

    @field:Min(0)
    val limiteJogadores: Int,

    @field:PositiveOrZero
    val valorPorJogador: BigDecimal = BigDecimal.ZERO,

    val chavePix: String? = null
)
