package com.bojogar.bot.repository

import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PeladaParticipantRepository : JpaRepository<PeladaParticipant, UUID> {

    fun findByPeladaId(peladaId: UUID): List<PeladaParticipant>

    fun findByUserId(userId: UUID): List<PeladaParticipant>

    fun findByPeladaIdAndUserId(peladaId: UUID, userId: UUID): PeladaParticipant?

    fun findByPeladaIdAndStatus(peladaId: UUID, status: ParticipantStatus): List<PeladaParticipant>

    fun countByPeladaIdAndStatus(peladaId: UUID, status: ParticipantStatus): Long

    fun findByPeladaIdAndStatusOrderByWaitlistPositionAsc(
        peladaId: UUID,
        status: ParticipantStatus
    ): List<PeladaParticipant>

    @Query("SELECT pp FROM PeladaParticipant pp WHERE pp.user.phone = :phone AND pp.status IN :statuses")
    fun findByUserPhoneAndStatusIn(phone: String, statuses: List<ParticipantStatus>): List<PeladaParticipant>

    @Query("SELECT pp FROM PeladaParticipant pp WHERE pp.user.phone = :phone AND pp.pelada.codigo = :codigo")
    fun findByUserPhoneAndPeladaCodigo(phone: String, codigo: String): PeladaParticipant?

    @Query("SELECT pp FROM PeladaParticipant pp WHERE pp.user.phone = :phone AND pp.role IN :roles AND pp.status = 'CONFIRMED'")
    fun findByUserPhoneAndRoleIn(phone: String, roles: List<com.bojogar.bot.enums.ParticipantRole>): List<PeladaParticipant>
}
