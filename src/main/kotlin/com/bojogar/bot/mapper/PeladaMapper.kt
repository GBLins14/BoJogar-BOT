package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.entity.Pelada
import org.springframework.stereotype.Component

@Component
class PeladaMapper {

    fun toResponse(entity: Pelada) = PeladaResponse(
        id = entity.id!!,
        codigo = entity.codigo,
        organizadorId = entity.organizador.id!!,
        esporte = entity.esporte,
        descricao = entity.descricao,
        dataHora = entity.dataHora,
        local = entity.local,
        limiteJogadores = entity.limiteJogadores,
        valorPorJogador = entity.valorPorJogador,
        chavePix = entity.chavePix,
        status = entity.status,
        criadoEm = entity.criadoEm,
        atualizadoEm = entity.atualizadoEm
    )
}
