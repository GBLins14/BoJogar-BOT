package com.bojogar.bot.service

import com.bojogar.bot.entity.Pagamento
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPagamento
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.repository.PagamentoRepository
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentSummary(
    val participantName: String,
    val participantPhone: String,
    val amount: BigDecimal,
    val status: StatusPagamento,
    val paidAt: Instant?
)

@Service
class PagamentoService(
    private val pagamentoRepository: PagamentoRepository,
    private val participantRepository: PeladaParticipantRepository,
    private val peladaRepository: PeladaRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(PagamentoService::class.java)
    }

    fun createPayment(participant: PeladaParticipant, amount: BigDecimal): Pagamento {
        return pagamentoRepository.save(
            Pagamento(participant = participant, valor = amount)
        )
    }

    @Transactional
    fun confirmPayment(participantId: UUID, requesterPhone: String): Pagamento {
        val payments = pagamentoRepository.findByParticipantId(participantId)
        val payment = payments.firstOrNull { it.status == StatusPagamento.PENDENTE }
            ?: throw BusinessException("Pagamento pendente nao encontrado")

        val requesterNormalized = PhoneUtils.normalizePhone(requesterPhone)
        val participant = payment.participant
        val pelada = participant.pelada

        val requesterParticipant = participantRepository.findByUserPhoneAndPeladaCodigo(requesterNormalized, pelada.codigo)
        if (requesterParticipant == null || !requesterParticipant.role.hasAuthority(com.bojogar.bot.enums.ParticipantRole.ADMIN)) {
            throw BusinessException("Sem permissao para confirmar pagamento")
        }

        payment.status = StatusPagamento.CONFIRMADO
        payment.paidAt = Instant.now()

        log.info("Payment confirmed for participant {} in pelada {} by {}", participantId, pelada.codigo, requesterNormalized)
        return pagamentoRepository.save(payment)
    }

    fun getPaymentStatus(peladaCode: String): List<PaymentSummary> {
        val payments = pagamentoRepository.findByParticipantPeladaCodigo(peladaCode.uppercase())
        return payments.map { p ->
            PaymentSummary(
                participantName = p.participant.displayName ?: p.participant.user.name,
                participantPhone = p.participant.user.phone,
                amount = p.valor,
                status = p.status,
                paidAt = p.paidAt
            )
        }
    }

    fun getUnpaidParticipants(peladaCode: String): List<PeladaParticipant> {
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase()) ?: return emptyList()
        val confirmed = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)

        return confirmed.filter { participant ->
            val payments = pagamentoRepository.findByParticipantId(participant.id!!)
            payments.none { it.status == StatusPagamento.CONFIRMADO }
        }
    }
}
