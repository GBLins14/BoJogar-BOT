package com.bojogar.bot.service

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.mapper.ParticipantMapper
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.util.PhoneUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthorizationService(
    private val participantRepository: PeladaParticipantRepository,
    private val participantMapper: ParticipantMapper
) {

    fun requireRole(phone: String, peladaCode: String, minRole: ParticipantRole): ParticipantResponse {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participant = participantRepository.findByUserPhoneAndPeladaCodigo(normalized, peladaCode.uppercase())
            ?: throw BusinessException("Voce nao participa desta pelada")

        if (participant.status !in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)) {
            throw BusinessException("Voce nao participa desta pelada")
        }

        if (!participant.role.hasAuthority(minRole)) {
            throw BusinessException("Sem permissao para esta acao")
        }

        return participantMapper.toResponse(participant)
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

    fun getManagedPeladas(phone: String): List<ParticipantResponse> {
        val normalized = PhoneUtils.normalizePhone(phone)
        return participantRepository.findByUserPhoneAndRoleIn(
            normalized,
            listOf(ParticipantRole.OWNER, ParticipantRole.ADMIN)
        ).map { participantMapper.toResponse(it) }
    }
}
