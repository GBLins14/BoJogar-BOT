package com.bojogar.bot.service

import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPagamento
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.mapper.PaymentMapper
import com.bojogar.bot.repository.PagamentoRepository
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PagamentoService(
    private val pagamentoRepository: PagamentoRepository,
    private val participantRepository: PeladaParticipantRepository,
    private val peladaRepository: PeladaRepository,
    private val paymentMapper: PaymentMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(PagamentoService::class.java)
    }

    @Transactional
    fun confirmPayment(participantId: UUID, requesterPhone: String): PaymentResponse {
        val payments = pagamentoRepository.findByParticipantId(participantId)
        val payment = payments.firstOrNull { it.status == StatusPagamento.PENDENTE }
            ?: throw BusinessException("Pagamento pendente nao encontrado")

        val requesterNormalized = PhoneUtils.normalizePhone(requesterPhone)
        val pelada = payment.participant.pelada

        val requesterParticipant = participantRepository.findByUserPhoneAndPeladaCodigo(requesterNormalized, pelada.codigo)
        if (requesterParticipant == null || !requesterParticipant.role.hasAuthority(com.bojogar.bot.enums.ParticipantRole.ADMIN)) {
            throw BusinessException("Sem permissao para confirmar pagamento")
        }

        payment.status = StatusPagamento.CONFIRMADO
        payment.paidAt = Instant.now()

        log.info("Payment confirmed for participant {} in pelada {} by {}", participantId, pelada.codigo, requesterNormalized)
        return paymentMapper.toResponse(pagamentoRepository.save(payment))
    }

    @Transactional(readOnly = true)
    fun getPaymentsByPelada(peladaCode: String): List<PaymentResponse> {
        return pagamentoRepository.findByParticipantPeladaCodigo(peladaCode.uppercase())
            .map { paymentMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getUnpaidParticipants(peladaCode: String): List<com.bojogar.bot.dto.response.ParticipantResponse> {
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase()) ?: return emptyList()
        val confirmed = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)

        val participantMapper = com.bojogar.bot.mapper.ParticipantMapper()
        return confirmed.filter { participant ->
            val payments = pagamentoRepository.findByParticipantId(participant.id!!)
            payments.none { it.status == StatusPagamento.CONFIRMADO }
        }.map { participantMapper.toResponse(it) }
    }
}
