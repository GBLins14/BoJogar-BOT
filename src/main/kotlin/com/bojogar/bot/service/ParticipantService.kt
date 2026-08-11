package com.bojogar.bot.service

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.entity.Pagamento
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPelada
import com.bojogar.bot.mapper.ParticipantMapper
import com.bojogar.bot.mapper.PeladaMapper
import com.bojogar.bot.repository.PagamentoRepository
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

// --- Result types (DTOs inside) ---

sealed interface JoinResult {
    data class Confirmed(val participant: ParticipantResponse, val pelada: PeladaResponse) : JoinResult
    data class PendingPayment(val participant: ParticipantResponse, val pelada: PeladaResponse) : JoinResult
    data class Waitlisted(val pelada: PeladaResponse, val position: Int) : JoinResult
    data object AlreadyJoined : JoinResult
    data object PeladaClosed : JoinResult
    data class Error(val message: String) : JoinResult
}

sealed interface LeaveResult {
    data class Left(val promoted: ParticipantResponse?) : LeaveResult
    data object NotFound : LeaveResult
    data class Error(val message: String) : LeaveResult
}

sealed interface RemoveResult {
    data class Removed(val promoted: ParticipantResponse?) : RemoveResult
    data object NotFound : RemoveResult
    data object Unauthorized : RemoveResult
    data class Error(val message: String) : RemoveResult
}

@Service
class ParticipantService(
    private val participantRepository: PeladaParticipantRepository,
    private val peladaRepository: PeladaRepository,
    private val userRepository: UserRepository,
    private val pagamentoRepository: PagamentoRepository,
    private val participantMapper: ParticipantMapper,
    private val peladaMapper: PeladaMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(ParticipantService::class.java)
    }

    @Transactional
    fun join(phone: String, peladaCode: String): JoinResult {
        val normalized = PhoneUtils.normalizePhone(phone)
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase())
            ?: return JoinResult.Error("Pelada não encontrada: $peladaCode")

        if (pelada.status !in listOf(StatusPelada.OPEN, StatusPelada.FULL)) {
            return JoinResult.PeladaClosed
        }

        val user = userRepository.findByPhone(normalized)
            ?: return JoinResult.Error("Usuário não encontrado")

        val existing = participantRepository.findByPeladaIdAndUserId(pelada.id!!, user.id!!)
        if (existing != null && existing.status in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)) {
            return JoinResult.AlreadyJoined
        }
        if (existing != null && existing.status == ParticipantStatus.PENDING_PAYMENT) {
            return JoinResult.PendingPayment(
                participantMapper.toResponse(existing),
                peladaMapper.toResponse(pelada)
            )
        }

        val isPaid = pelada.valorPorJogador > BigDecimal.ZERO

        return synchronized(peladaCode.uppercase().intern()) {
            val confirmedCount = participantRepository.countByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)
            val hasSlot = pelada.limiteJogadores == 0 || confirmedCount < pelada.limiteJogadores

            if (isPaid) {
                // Paid pelada: participant starts as PENDING_PAYMENT (doesn't take a slot)
                val participant = participantRepository.save(
                    PeladaParticipant(
                        user = user,
                        pelada = pelada,
                        role = ParticipantRole.PLAYER,
                        displayName = user.name,
                        status = ParticipantStatus.PENDING_PAYMENT
                    )
                )

                createPaymentIfNeeded(participant, pelada)

                log.info("User {} joined pelada {} as PENDING_PAYMENT", normalized, peladaCode)
                JoinResult.PendingPayment(participantMapper.toResponse(participant), peladaMapper.toResponse(pelada))
            } else if (hasSlot) {
                // Free pelada: confirm immediately
                val participant = participantRepository.save(
                    PeladaParticipant(
                        user = user,
                        pelada = pelada,
                        role = ParticipantRole.PLAYER,
                        displayName = user.name,
                        status = ParticipantStatus.CONFIRMED
                    )
                )

                if (pelada.limiteJogadores > 0 && confirmedCount + 1 >= pelada.limiteJogadores && pelada.status == StatusPelada.OPEN) {
                    pelada.status = StatusPelada.FULL
                    peladaRepository.save(pelada)
                    log.info("Pelada {} is now FULL", peladaCode)
                }

                log.info("User {} joined pelada {} as CONFIRMED", normalized, peladaCode)
                JoinResult.Confirmed(participantMapper.toResponse(participant), peladaMapper.toResponse(pelada))
            } else {
                // Free pelada but full: waitlist
                val waitlistCount = participantRepository.countByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.WAITLIST)
                val position = (waitlistCount + 1).toInt()

                participantRepository.save(
                    PeladaParticipant(
                        user = user,
                        pelada = pelada,
                        role = ParticipantRole.PLAYER,
                        displayName = user.name,
                        status = ParticipantStatus.WAITLIST,
                        waitlistPosition = position
                    )
                )

                log.info("User {} added to waitlist #{} for pelada {}", normalized, position, peladaCode)
                JoinResult.Waitlisted(peladaMapper.toResponse(pelada), position)
            }
        }
    }

    @Transactional
    fun leave(phone: String, peladaCode: String): LeaveResult {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participant = participantRepository.findByUserPhoneAndPeladaCodigo(normalized, peladaCode.uppercase())
            ?: return LeaveResult.NotFound

        if (participant.role == ParticipantRole.OWNER) {
            return LeaveResult.Error("O organizador não pode sair da pelada. Use /gerenciar cancelar para cancelar.")
        }

        if (participant.status !in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.PENDING_PAYMENT, ParticipantStatus.WAITLIST)) {
            return LeaveResult.NotFound
        }

        val wasConfirmed = participant.status == ParticipantStatus.CONFIRMED
        participant.status = ParticipantStatus.CANCELLED
        participant.waitlistPosition = null
        participantRepository.save(participant)

        var promoted: ParticipantResponse? = null
        if (wasConfirmed) {
            promoted = promoteFromWaitlist(participant.pelada.id!!)
            updatePeladaStatusAfterLeave(participant.pelada)
        } else {
            recalculateWaitlistPositions(participant.pelada.id!!)
        }

        log.info("User {} left pelada {}", normalized, peladaCode)
        return LeaveResult.Left(promoted)
    }

    @Transactional
    fun removeParticipant(requesterPhone: String, targetPhone: String, peladaCode: String): RemoveResult {
        val normalizedRequester = PhoneUtils.normalizePhone(requesterPhone)
        val normalizedTarget = PhoneUtils.normalizePhone(targetPhone)

        val requester = participantRepository.findByUserPhoneAndPeladaCodigo(normalizedRequester, peladaCode.uppercase())
            ?: return RemoveResult.Unauthorized

        if (!requester.role.hasAuthority(ParticipantRole.ADMIN)) {
            return RemoveResult.Unauthorized
        }

        val target = participantRepository.findByUserPhoneAndPeladaCodigo(normalizedTarget, peladaCode.uppercase())
            ?: return RemoveResult.NotFound

        if (target.role == ParticipantRole.OWNER) {
            return RemoveResult.Error("Nao e possivel remover o organizador")
        }

        val wasConfirmed = target.status == ParticipantStatus.CONFIRMED
        target.status = ParticipantStatus.REMOVED
        target.waitlistPosition = null
        participantRepository.save(target)

        var promoted: ParticipantResponse? = null
        if (wasConfirmed) {
            promoted = promoteFromWaitlist(target.pelada.id!!)
            updatePeladaStatusAfterLeave(target.pelada)
        } else {
            recalculateWaitlistPositions(target.pelada.id!!)
        }

        log.info("User {} removed {} from pelada {}", normalizedRequester, normalizedTarget, peladaCode)
        return RemoveResult.Removed(promoted)
    }

    @Transactional(readOnly = true)
    fun getParticipants(peladaCode: String): List<ParticipantResponse> {
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase()) ?: return emptyList()
        return participantRepository.findByPeladaId(pelada.id!!).map { participantMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getActiveParticipants(peladaCode: String): List<ParticipantResponse> {
        return getParticipants(peladaCode).filter {
            it.status in listOf(ParticipantStatus.CONFIRMED.name, ParticipantStatus.PENDING_PAYMENT.name, ParticipantStatus.WAITLIST.name)
        }
    }

    @Transactional(readOnly = true)
    fun getUserRole(phone: String, peladaCode: String): ParticipantRole? {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participant = participantRepository.findByUserPhoneAndPeladaCodigo(normalized, peladaCode.uppercase())
        return if (participant != null && participant.status in listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)) {
            participant.role
        } else null
    }

    @Transactional
    fun assignRole(requesterPhone: String, targetPhone: String, peladaCode: String, role: ParticipantRole): Boolean {
        val normalizedRequester = PhoneUtils.normalizePhone(requesterPhone)
        val normalizedTarget = PhoneUtils.normalizePhone(targetPhone)

        val requester = participantRepository.findByUserPhoneAndPeladaCodigo(normalizedRequester, peladaCode.uppercase())
            ?: return false

        if (!requester.role.hasAuthority(ParticipantRole.OWNER)) return false
        if (role == ParticipantRole.OWNER) return false

        val target = participantRepository.findByUserPhoneAndPeladaCodigo(normalizedTarget, peladaCode.uppercase())
            ?: return false

        target.role = role
        participantRepository.save(target)

        log.info("User {} role changed to {} in pelada {} by {}", normalizedTarget, role, peladaCode, normalizedRequester)
        return true
    }

    @Transactional(readOnly = true)
    fun getUserParticipations(phone: String, activeOnly: Boolean = true): List<ParticipantResponse> {
        val normalized = PhoneUtils.normalizePhone(phone)
        val statuses = if (activeOnly) {
            listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.PENDING_PAYMENT, ParticipantStatus.WAITLIST)
        } else {
            ParticipantStatus.entries
        }
        return participantRepository.findByUserPhoneAndStatusIn(normalized, statuses)
            .map { participantMapper.toResponse(it) }
    }

    private fun promoteFromWaitlist(peladaId: UUID): ParticipantResponse? {
        val waitlisted = participantRepository.findByPeladaIdAndStatusOrderByWaitlistPositionAsc(
            peladaId, ParticipantStatus.WAITLIST
        )

        val first = waitlisted.firstOrNull() ?: return null
        first.status = ParticipantStatus.CONFIRMED
        first.waitlistPosition = null
        participantRepository.save(first)

        recalculateWaitlistPositions(peladaId)

        log.info("Promoted user {} from waitlist for pelada {}", first.user.phone, peladaId)
        return participantMapper.toResponse(first)
    }

    private fun createPaymentIfNeeded(participant: PeladaParticipant, pelada: com.bojogar.bot.entity.Pelada) {
        if (pelada.valorPorJogador > BigDecimal.ZERO) {
            pagamentoRepository.save(
                Pagamento(participant = participant, valor = pelada.valorPorJogador)
            )
        }
    }

    private fun updatePeladaStatusAfterLeave(pelada: com.bojogar.bot.entity.Pelada) {
        if (pelada.status == StatusPelada.FULL) {
            val confirmed = participantRepository.countByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)
            if (pelada.limiteJogadores == 0 || confirmed < pelada.limiteJogadores) {
                pelada.status = StatusPelada.OPEN
                peladaRepository.save(pelada)
                log.info("Pelada {} back to OPEN ({}/{})", pelada.codigo, confirmed, pelada.limiteJogadores)
            }
        }
    }

    private fun recalculateWaitlistPositions(peladaId: UUID) {
        val waitlisted = participantRepository.findByPeladaIdAndStatusOrderByWaitlistPositionAsc(
            peladaId, ParticipantStatus.WAITLIST
        )
        waitlisted.forEachIndexed { index, p ->
            p.waitlistPosition = index + 1
            participantRepository.save(p)
        }
    }
}
