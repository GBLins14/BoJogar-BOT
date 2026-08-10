package com.bojogar.bot.dto.request

import jakarta.validation.constraints.NotBlank

data class InscricaoRequest(
    @field:NotBlank
    val peladaCodigo: String,

    @field:NotBlank
    val telefone: String
)
