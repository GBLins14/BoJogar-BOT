package com.bojogar.bot.service

import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.util.PhoneUtils
import org.springframework.stereotype.Service

@Service
class AuthorizationService(
    private val participantRepository: PeladaParticipantRepository
) {

    fun requireRole(phone: String, peladaCode: String, minRole: ParticipantRole): PeladaParticipant {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participant = participantRepository.findByUserPhoneAndPeladaCodigo(normalized, peladaCode.uppercase())
            ?: throw BusinessException("Voce nao participa desta pelada")

        if (participant.status !in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)) {
            throw BusinessException("Voce nao participa desta pelada")
        }

        if (!participant.role.hasAuthority(minRole)) {
            throw BusinessException("Sem permissao para esta acao")
        }

        return participant
    }

    fun hasRole(phone: String, peladaCode: String, minRole: ParticipantRole): Boolean {
        return try {
            requireRole(phone, peladaCode, minRole)
            true
        } catch (_: BusinessException) {
            false
        }
    }

    fun isOwner(phone: String, peladaCode: String): Boolean {
        return hasRole(phone, peladaCode, ParticipantRole.OWNER)
    }

    fun isAdminOrOwner(phone: String, peladaCode: String): Boolean {
        return hasRole(phone, peladaCode, ParticipantRole.ADMIN)
    }

    fun getManagedPeladas(phone: String): List<PeladaParticipant> {
        val normalized = PhoneUtils.normalizePhone(phone)
        return participantRepository.findByUserPhoneAndRoleIn(
            normalized,
            listOf(ParticipantRole.OWNER, ParticipantRole.ADMIN)
        )
    }
}
