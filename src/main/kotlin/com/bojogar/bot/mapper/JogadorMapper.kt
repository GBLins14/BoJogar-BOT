package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.JogadorResponse
import com.bojogar.bot.entity.Jogador
import org.springframework.stereotype.Component

@Component
class JogadorMapper {

    fun toResponse(entity: Jogador) = JogadorResponse(
        id = entity.id!!,
        nome = entity.nome,
        telefone = entity.telefone,
        criadoEm = entity.criadoEm,
        atualizadoEm = entity.atualizadoEm
    )
}
