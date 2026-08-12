package com.bojogar.bot.dto.request

import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreatePeladaRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val esporte: String,

    @field:Size(max = 200)
    val descricao: String? = null,

    @field:NotNull
    val dataHora: LocalDateTime,

    @field:NotBlank
    @field:Size(min = 5, max = 200)
    val local: String,

    @field:Min(0)
    val limiteJogadores: Int,

    @field:PositiveOrZero
    val valorPorJogador: BigDecimal = BigDecimal.ZERO,

    val chavePix: String? = null
)
