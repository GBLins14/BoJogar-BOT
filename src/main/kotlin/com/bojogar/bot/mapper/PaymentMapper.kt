package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.entity.Pagamento
import org.springframework.stereotype.Component

@Component
class PaymentMapper {

    fun toResponse(entity: Pagamento): PaymentResponse = PaymentResponse(
        id = entity.id!!,
        participantId = entity.participant.id!!,
        participantName = entity.participant.displayName ?: entity.participant.user.name,
        participantPhone = entity.participant.user.phone,
        valor = entity.valor,
        status = entity.status.name,
        transactionId = entity.transactionId,
        paidAt = entity.paidAt
    )
}
