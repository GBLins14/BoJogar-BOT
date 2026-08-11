package com.bojogar.bot.service

import com.bojogar.bot.dto.request.CreatePeladaRequest
import com.bojogar.bot.dto.request.UpdatePeladaRequest
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.*
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.mapper.PeladaMapper
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.util.CodeGenerator
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PeladaService(
    private val peladaRepository: PeladaRepository,
    private val userRepository: UserRepository,
    private val participantRepository: PeladaParticipantRepository,
    private val peladaMapper: PeladaMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(PeladaService::class.java)
        private const val MAX_CODE_ATTEMPTS = 10
    }

    @Transactional
    fun create(phone: String, request: CreatePeladaRequest): PeladaResponse {
        val normalized = PhoneUtils.normalizePhone(phone)
        val user = userRepository.findByPhone(normalized)
            ?: throw BusinessException("Usuário não encontrado")

        val sport = runCatching { Esporte.valueOf(request.esporte.uppercase()) }.getOrElse {
            throw BusinessException("Esporte inválido: ${request.esporte}")
        }

        val code = generateUniqueCode()

        val pelada = peladaRepository.save(
            Pelada(
                codigo = code,
                createdBy = user,
                esporte = sport,
                descricao = request.descricao,
                dataHora = request.dataHora,
                local = request.local,
                limiteJogadores = request.limiteJogadores,
                valorPorJogador = request.valorPorJogador,
                chavePix = request.chavePix,
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
        return peladaMapper.toResponse(pelada)
    }

    @Transactional(readOnly = true)
    fun findByCode(code: String): PeladaResponse? {
        val pelada = peladaRepository.findByCodigo(code.uppercase()) ?: return null
        return peladaMapper.toResponse(pelada)
    }

    @Transactional(readOnly = true)
    fun findByUser(phone: String): List<PeladaResponse> {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participations = participantRepository.findByUserPhoneAndStatusIn(
            normalized,
            listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.WAITLIST)
        )
        return participations.map { peladaMapper.toResponse(it.pelada) }
    }

    @Transactional(readOnly = true)
    fun findCreatedByUser(phone: String): List<PeladaResponse> {
        return peladaRepository.findByCreatedByPhone(PhoneUtils.normalizePhone(phone))
            .map { peladaMapper.toResponse(it) }
    }

    @Transactional
    fun update(code: String, requesterPhone: String, request: UpdatePeladaRequest): PeladaResponse {
        val pelada = peladaRepository.findByCodigo(code.uppercase())
            ?: throw BusinessException("Pelada não encontrada: $code")

        request.descricao?.let { pelada.descricao = it }
        request.dataHora?.let { pelada.dataHora = it }
        request.local?.let { pelada.local = it }
        request.limiteJogadores?.let {
            if (it != 0 && it < 2) throw BusinessException("Mínimo de 2 jogadores (ou 0 para sem limite)")
            pelada.limiteJogadores = it
        }
        request.valorPorJogador?.let { pelada.valorPorJogador = it }
        request.chavePix?.let { pelada.chavePix = it }

        log.info("Pelada {} updated by {}", code, requesterPhone)
        return peladaMapper.toResponse(peladaRepository.save(pelada))
    }

    @Transactional
    fun cancel(code: String, requesterPhone: String): PeladaResponse {
        val pelada = peladaRepository.findByCodigo(code.uppercase())
            ?: throw BusinessException("Pelada não encontrada: $code")

        if (!pelada.status.canTransitionTo(StatusPelada.CANCELLED)) {
            throw BusinessException("Pelada não pode ser cancelada no status ${pelada.status}")
        }

        val participants = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED) +
            participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.PENDING_PAYMENT) +
            participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.WAITLIST)

        participants.forEach { p ->
            p.status = ParticipantStatus.CANCELLED
            participantRepository.save(p)
        }

        pelada.status = StatusPelada.CANCELLED
        log.info("Pelada {} cancelled by {}, {} participants affected", code, requesterPhone, participants.size)
        return peladaMapper.toResponse(peladaRepository.save(pelada))
    }

    private fun generateUniqueCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = CodeGenerator.generatePeladaCode()
            if (peladaRepository.findByCodigo(code) == null) return code
        }
        throw BusinessException("Falha ao gerar código único")
    }
}
