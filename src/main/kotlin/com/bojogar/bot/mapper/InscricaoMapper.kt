package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.InscricaoResponse
import com.bojogar.bot.entity.Inscricao
import org.springframework.stereotype.Component

@Component
class InscricaoMapper {

    fun toResponse(entity: Inscricao) = InscricaoResponse(
        id = entity.id!!,
        peladaId = entity.pelada.id!!,
        jogadorId = entity.jogador.id!!,
        status = entity.status,
        posicaoListaEspera = entity.posicaoListaEspera,
        criadoEm = entity.criadoEm,
        atualizadoEm = entity.atualizadoEm
    )
}
