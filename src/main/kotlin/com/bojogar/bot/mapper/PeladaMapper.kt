package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.repository.PeladaParticipantRepository
import org.springframework.stereotype.Component

@Component
class PeladaMapper(
    private val participantRepository: PeladaParticipantRepository
) {

    fun toResponse(entity: Pelada): PeladaResponse {
        val confirmed = participantRepository.countByPeladaIdAndStatus(entity.id!!, ParticipantStatus.CONFIRMED)
        val waitlist = participantRepository.countByPeladaIdAndStatus(entity.id!!, ParticipantStatus.WAITLIST)
        val remaining = if (entity.limiteJogadores == 0) Int.MAX_VALUE else (entity.limiteJogadores - confirmed).toInt().coerceAtLeast(0)

        return PeladaResponse(
            id = entity.id!!,
            codigo = entity.codigo,
            esporte = entity.esporte.name,
            esporteLabel = entity.esporte.label,
            descricao = entity.descricao,
            dataHora = entity.dataHora,
            local = entity.local,
            limiteJogadores = entity.limiteJogadores,
            valorPorJogador = entity.valorPorJogador,
            chavePix = entity.chavePix,
            status = entity.status.name,
            createdByName = entity.createdBy.name,
            createdByPhone = entity.createdBy.phone,
            confirmedCount = confirmed,
            remainingSlots = remaining,
            waitlistCount = waitlist,
            createdAt = entity.criadoEm
        )
    }
}
