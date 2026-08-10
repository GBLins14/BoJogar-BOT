package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.PagamentoResponse
import com.bojogar.bot.entity.Pagamento
import org.springframework.stereotype.Component

@Component
class PagamentoMapper {

    fun toResponse(entity: Pagamento) = PagamentoResponse(
        id = entity.id!!,
        inscricaoId = entity.inscricao.id!!,
        valor = entity.valor,
        status = entity.status,
        transactionId = entity.transactionId,
        criadoEm = entity.criadoEm,
        atualizadoEm = entity.atualizadoEm
    )
}
