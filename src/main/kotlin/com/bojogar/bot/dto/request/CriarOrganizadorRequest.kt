package com.bojogar.bot.dto.request

import jakarta.validation.constraints.NotBlank

data class CriarOrganizadorRequest(
    @field:NotBlank
    val nome: String,

    @field:NotBlank
    val telefone: String,

    val email: String? = null
)
