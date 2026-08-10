package com.bojogar.bot.service

import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.*
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.util.CodeGenerator
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PeladaService(
    private val peladaRepository: PeladaRepository,
    private val userRepository: UserRepository,
    private val participantRepository: PeladaParticipantRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(PeladaService::class.java)
        private const val MAX_CODE_ATTEMPTS = 10
    }

    @Transactional
    fun create(
        phone: String,
        sport: Esporte,
        description: String?,
        dateTime: LocalDateTime,
        location: String,
        maxPlayers: Int,
        pricePerPlayer: BigDecimal,
        pixKey: String?
    ): Pelada {
        val normalized = PhoneUtils.normalizePhone(phone)
        val user = userRepository.findByPhone(normalized)
            ?: throw BusinessException("Usuario nao encontrado")

        val code = generateUniqueCode()

        val pelada = peladaRepository.save(
            Pelada(
                codigo = code,
                createdBy = user,
                esporte = sport,
                descricao = description,
                dataHora = dateTime,
                local = location,
                limiteJogadores = maxPlayers,
                valorPorJogador = pricePerPlayer,
                chavePix = pixKey,
                status = StatusPelada.OPEN
            )
        )

        participantRepository.save(
            PeladaParticipant(
                user = user,
                pelada = pelada,
                role = ParticipantRole.OWNER,
                displayName = user.name,
                status = ParticipantStatus.CONFIRMED
            )
        )

        log.info("Pelada created: {} by {} ({})", code, user.name, normalized)
        return pelada
    }

    fun findByCode(code: String): Pelada? {
        return peladaRepository.findByCodigo(code.uppercase())
    }

    fun findOpenPeladas(): List<Pelada> {
        return peladaRepository.findByStatusInAndDataHoraAfter(
            listOf(StatusPelada.OPEN, StatusPelada.FULL),
            LocalDateTime.now()
        )
    }

    fun findByUser(phone: String): List<Pelada> {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participations = participantRepository.findByUserPhoneAndStatusIn(
            normalized,
            listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)
        )
        return participations.map { it.pelada }
    }

    fun findCreatedByUser(phone: String): List<Pelada> {
        return peladaRepository.findByCreatedByPhone(PhoneUtils.normalizePhone(phone))
    }

    @Transactional
    fun updateStatus(code: String, newStatus: StatusPelada, requesterPhone: String): Pelada {
        val pelada = findByCode(code) ?: throw BusinessException("Pelada nao encontrada: $code")

        if (!pelada.status.canTransitionTo(newStatus)) {
            throw BusinessException("Transicao invalida: ${pelada.status} -> $newStatus")
        }

        pelada.status = newStatus
        log.info("Pelada {} status: {} -> {}", code, pelada.status, newStatus)
        return peladaRepository.save(pelada)
    }

    @Transactional
    fun cancel(code: String, requesterPhone: String): Pelada {
        val pelada = findByCode(code) ?: throw BusinessException("Pelada nao encontrada: $code")

        if (!pelada.status.canTransitionTo(StatusPelada.CANCELLED)) {
            throw BusinessException("Pelada nao pode ser cancelada no status ${pelada.status}")
        }

        val participants = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED) +
            participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.WAITLIST)

        participants.forEach { p ->
            p.status = ParticipantStatus.CANCELLED
            participantRepository.save(p)
        }

        pelada.status = StatusPelada.CANCELLED
        log.info("Pelada {} cancelled by {}, {} participants affected", code, requesterPhone, participants.size)
        return peladaRepository.save(pelada)
    }

    fun getConfirmedCount(pelada: Pelada): Long {
        return participantRepository.countByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)
    }

    fun getRemainingSlots(pelada: Pelada): Int {
        val confirmed = getConfirmedCount(pelada)
        return (pelada.limiteJogadores - confirmed).toInt().coerceAtLeast(0)
    }

    private fun generateUniqueCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = CodeGenerator.generatePeladaCode()
            if (peladaRepository.findByCodigo(code) == null) return code
        }
        throw BusinessException("Falha ao gerar codigo unico")
    }
}
