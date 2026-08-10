package com.bojogar.bot.mapper

import com.bojogar.bot.dto.request.CriarOrganizadorRequest
import com.bojogar.bot.dto.response.OrganizadorResponse
import com.bojogar.bot.entity.Organizador
import org.springframework.stereotype.Component

@Component
class OrganizadorMapper {

    fun toResponse(entity: Organizador) = OrganizadorResponse(
        id = entity.id!!,
        nome = entity.nome,
        telefone = entity.telefone,
        email = entity.email,
        criadoEm = entity.criadoEm,
        atualizadoEm = entity.atualizadoEm
    )

    fun toEntity(request: CriarOrganizadorRequest) = Organizador(
        nome = request.nome,
        telefone = request.telefone,
        email = request.email
    )
}
