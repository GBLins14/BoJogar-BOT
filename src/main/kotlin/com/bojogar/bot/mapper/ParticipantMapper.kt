package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.entity.PeladaParticipant
import org.springframework.stereotype.Component

@Component
class ParticipantMapper {

    fun toResponse(entity: PeladaParticipant): ParticipantResponse = ParticipantResponse(
        id = entity.id!!,
        userId = entity.user.id!!,
        userName = entity.user.name,
        userPhone = entity.user.phone,
        peladaCodigo = entity.pelada.codigo,
        role = entity.role.name,
        displayName = entity.displayName,
        shirtNumber = entity.shirtNumber,
        status = entity.status.name,
        waitlistPosition = entity.waitlistPosition,
        joinedAt = entity.joinedAt
    )
}
