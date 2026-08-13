package com.bojogar.bot.service

import com.bojogar.bot.config.AbacatePayProperties
import com.bojogar.bot.dto.abacatepay.AbacatePayCustomer
import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.enums.StatusPagamento
import com.bojogar.bot.exception.BusinessException
import com.bojogar.bot.mapper.ParticipantMapper
import com.bojogar.bot.mapper.PaymentMapper
import com.bojogar.bot.repository.PagamentoRepository
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface PixGenerationResult {
    data class Success(val pixCode: String, val paymentId: UUID) : PixGenerationResult
    data class Error(val message: String) : PixGenerationResult
}

@Service
class PagamentoService(
    private val pagamentoRepository: PagamentoRepository,
    private val participantRepository: PeladaParticipantRepository,
    private val peladaRepository: PeladaRepository,
    private val paymentMapper: PaymentMapper,
    private val participantMapper: ParticipantMapper,
    private val peladaMapper: com.bojogar.bot.mapper.PeladaMapper,
    private val abacatePayClient: AbacatePayClient,
    private val abacatePayProperties: AbacatePayProperties,
    private val notificationService: NotificationService
) {

    companion object {
        private val log = LoggerFactory.getLogger(PagamentoService::class.java)
        val PIX_EXPIRATION: Duration = Duration.ofMinutes(30)
    }

    @Transactional
    fun confirmPayment(participantId: UUID, requesterPhone: String): PaymentResponse {
        val payments = pagamentoRepository.findByParticipantId(participantId)
        val payment = payments.firstOrNull { it.status == StatusPagamento.PENDENTE }
            ?: throw BusinessException("Pagamento pendente não encontrado")

        val requesterNormalized = PhoneUtils.normalizePhone(requesterPhone)
        val participant = payment.participant
        val pelada = participant.pelada

        val requesterParticipant = participantRepository.findByUserPhoneAndPeladaCodigo(requesterNormalized, pelada.codigo)
        if (requesterParticipant == null || !requesterParticipant.role.hasAuthority(com.bojogar.bot.enums.ParticipantRole.ADMIN)) {
            throw BusinessException("Sem permissão para confirmar pagamento")
        }

        payment.status = StatusPagamento.CONFIRMADO
        payment.paidAt = Instant.now()

        // Promote participant from PENDING_PAYMENT to CONFIRMED
        if (participant.status == ParticipantStatus.PENDING_PAYMENT) {
            participant.status = ParticipantStatus.CONFIRMED
            participantRepository.save(participant)
            updatePeladaStatusAfterPayment(pelada)
            log.info("Participant {} promoted to CONFIRMED after manual payment confirmation", participantId)
        }

        log.info("Payment confirmed for participant {} in pelada {} by {}", participantId, pelada.codigo, requesterNormalized)
        return paymentMapper.toResponse(pagamentoRepository.save(payment))
    }

    @Transactional
    fun generatePix(
        participantId: UUID,
        userName: String,
        userPhone: String,
        userCpf: String,
        userEmail: String
    ): PixGenerationResult {
        val payments = pagamentoRepository.findByParticipantId(participantId)
        val payment = payments.firstOrNull { it.status == StatusPagamento.PENDENTE }
            ?: return PixGenerationResult.Error("Pagamento pendente não encontrado")

        // If PIX already generated and not expired, return existing code
        if (payment.pixCode != null && payment.syncpayIdentifier != null) {
            val generatedAt = payment.pixGeneratedAt
            if (generatedAt != null && Instant.now().isBefore(generatedAt.plus(PIX_EXPIRATION))) {
                return PixGenerationResult.Success(payment.pixCode!!, payment.id!!)
            }
            // PIX expired — clear and regenerate
            log.info("PIX expired for participant {} - regenerating", participantId)
            payment.pixCode = null
            payment.syncpayIdentifier = null
            payment.pixGeneratedAt = null
        }

        return try {
            val pelada = payment.participant.pelada
            val customer = AbacatePayCustomer(
                name = userName,
                taxId = userCpf,
                email = userEmail,
                cellphone = userPhone.takeLast(11)
            )

            val response = abacatePayClient.generatePix(
                amount = payment.valor,
                description = "Pelada ${pelada.codigo} - ${pelada.esporte.label}",
                expiresInSeconds = PIX_EXPIRATION.toSeconds().toInt(),
                customer = customer
            )

            if (response.brCode != null && response.id != null) {
                payment.pixCode = response.brCode
                payment.syncpayIdentifier = response.id
                payment.pixGeneratedAt = Instant.now()
                pagamentoRepository.save(payment)

                log.info("PIX generated for participant {} in pelada {} - id: {}",
                    participantId, pelada.codigo, response.id)
                PixGenerationResult.Success(response.brCode, payment.id!!)
            } else {
                log.error("AbacatePay returned empty brCode or id")
                PixGenerationResult.Error("Erro ao gerar PIX. Tente novamente.")
            }
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            log.error("AbacatePay client error for participant {}: {}", participantId, e.responseBodyAsString, e)
            val apiError = try {
                val body = e.responseBodyAsString
                val errorMsg = body.substringAfter("\"error\":\"").substringBefore("\"")
                when {
                    errorMsg.contains("taxId", ignoreCase = true) -> "CPF inválido. Verifique e tente novamente."
                    errorMsg.isNotBlank() -> "Erro: $errorMsg"
                    else -> "Erro ao gerar PIX. Tente novamente."
                }
            } catch (_: Exception) {
                "Erro ao gerar PIX. Tente novamente."
            }
            PixGenerationResult.Error(apiError)
        } catch (e: Exception) {
            log.error("Failed to generate PIX for participant {}: {}", participantId, e.message, e)
            PixGenerationResult.Error("Erro ao gerar PIX. Tente novamente.")
        }
    }

    @Transactional
    fun processWebhookPayment(syncpayIdentifier: String, endToEnd: String?) {
        val payment = pagamentoRepository.findBySyncpayIdentifier(syncpayIdentifier)
        if (payment == null) {
            log.warn("No matching pending payment found for identifier: {}", syncpayIdentifier)
            return
        }

        // Idempotent: skip if already confirmed
        if (payment.status == StatusPagamento.CONFIRMADO) {
            log.info("Payment already confirmed for identifier: {}", syncpayIdentifier)
            return
        }

        payment.status = StatusPagamento.CONFIRMADO
        payment.paidAt = Instant.now()
        payment.transactionId = endToEnd
        pagamentoRepository.save(payment)

        // Access lazy fields while session is still open
        val participant = payment.participant

        // Promote participant from PENDING_PAYMENT to CONFIRMED
        if (participant.status == ParticipantStatus.PENDING_PAYMENT) {
            participant.status = ParticipantStatus.CONFIRMED
            participantRepository.save(participant)
            val pelada2 = participant.pelada
            updatePeladaStatusAfterPayment(pelada2)
            log.info("Participant {} promoted to CONFIRMED after webhook payment", participant.id)
        }
        val pelada = participant.pelada
        val participantPhone = participant.user.phone
        val participantName = participant.displayName ?: participant.user.name
        val peladaCode = pelada.codigo
        val amount = payment.valor

        log.info("Payment confirmed via webhook - identifier: {}, endToEnd: {}", syncpayIdentifier, endToEnd)

        // Send notifications
        val peladaResponse = peladaMapper.toResponse(pelada)

        notificationService.notifyPaymentConfirmed(
            participantPhone = participantPhone,
            participantName = participantName,
            pelada = peladaResponse
        )
        notificationService.notifyAdminPaymentReceived(
            peladaCode = peladaCode,
            participantName = participantName,
            amount = amount
        )

        log.info("Payment confirmed via webhook for participant {} in pelada {}", participantPhone, peladaCode)
    }

    @Transactional
    fun processWebhookRefund(syncpayIdentifier: String) {
        val payment = pagamentoRepository.findBySyncpayIdentifier(syncpayIdentifier)
        if (payment == null) {
            log.warn("No matching payment found for refund - identifier: {}", syncpayIdentifier)
            return
        }

        if (payment.status == StatusPagamento.ESTORNADO) {
            log.info("Payment already refunded for identifier: {}", syncpayIdentifier)
            return
        }

        payment.status = StatusPagamento.ESTORNADO
        pagamentoRepository.save(payment)

        val participant = payment.participant

        // Revert participant to PENDING_PAYMENT if they were CONFIRMED
        if (participant.status == ParticipantStatus.CONFIRMED) {
            participant.status = ParticipantStatus.PENDING_PAYMENT
            participantRepository.save(participant)
            log.info("Participant {} reverted to PENDING_PAYMENT after refund", participant.id)

            val pelada = participant.pelada
            if (pelada.status == com.bojogar.bot.enums.StatusPelada.FULL) {
                pelada.status = com.bojogar.bot.enums.StatusPelada.OPEN
                peladaRepository.save(pelada)
                log.info("Pelada {} reopened after refund", pelada.codigo)
            }
        }

        val participantPhone = participant.user.phone
        val participantName = participant.displayName ?: participant.user.name
        val pelada = participant.pelada

        log.info("Payment refunded via webhook - identifier: {}, participant: {}", syncpayIdentifier, participantPhone)

        val peladaResponse = peladaMapper.toResponse(pelada)

        notificationService.notifyPaymentRefunded(
            participantPhone = participantPhone,
            participantName = participantName,
            pelada = peladaResponse,
            amount = payment.valor
        )
    }

    @Transactional(readOnly = true)
    fun getPaymentsByPelada(peladaCode: String): List<PaymentResponse> {
        return pagamentoRepository.findByParticipantPeladaCodigo(peladaCode.uppercase())
            .map { paymentMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getUnpaidParticipants(peladaCode: String): List<com.bojogar.bot.dto.response.ParticipantResponse> {
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase()) ?: return emptyList()
        val pendingPayment = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.PENDING_PAYMENT)

        return pendingPayment.map { participantMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun findPendingPaymentForUser(phone: String, peladaCode: String): PaymentResponse? {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participant = participantRepository.findByUserPhoneAndPeladaCodigo(normalized, peladaCode.uppercase())
            ?: return null
        val payments = pagamentoRepository.findByParticipantId(participant.id!!)
        val pending = payments.firstOrNull { it.status == StatusPagamento.PENDENTE }
            ?: return null
        return paymentMapper.toResponse(pending)
    }

    @Transactional(readOnly = true)
    fun getUserPendingPayments(phone: String): List<PaymentResponse> {
        val normalized = PhoneUtils.normalizePhone(phone)
        val participations = participantRepository.findByUserPhoneAndStatusIn(
            normalized,
            listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.PENDING_PAYMENT, ParticipantStatus.WAITLIST)
        )
        return participations.flatMap { participant ->
            pagamentoRepository.findByParticipantId(participant.id!!)
                .filter { it.status == StatusPagamento.PENDENTE }
                .map { paymentMapper.toResponse(it) }
        }
    }

    private fun updatePeladaStatusAfterPayment(pelada: com.bojogar.bot.entity.Pelada) {
        if (pelada.limiteJogadores > 0 && pelada.status == com.bojogar.bot.enums.StatusPelada.OPEN) {
            val confirmedCount = participantRepository.countByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)
            if (confirmedCount >= pelada.limiteJogadores) {
                pelada.status = com.bojogar.bot.enums.StatusPelada.FULL
                peladaRepository.save(pelada)
                log.info("Pelada {} is now FULL after payment confirmation", pelada.codigo)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getWalletBalance(peladaCode: String): BigDecimal {
        val payments = pagamentoRepository.findByParticipantPeladaCodigo(peladaCode.uppercase())
        val totalCollected = payments
            .filter { it.status == StatusPagamento.CONFIRMADO }
            .sumOf { it.valor }

        if (totalCollected <= BigDecimal.ZERO) return BigDecimal.ZERO

        val platformFee = totalCollected
            .multiply(BigDecimal(abacatePayProperties.platformFeePercent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

        return totalCollected.subtract(platformFee).max(BigDecimal.ZERO)
    }

    @Transactional(readOnly = true)
    fun getOrganizerWalletBalance(phone: String): BigDecimal {
        val normalized = PhoneUtils.normalizePhone(phone)
        val managed = participantRepository.findByUserPhoneAndStatusIn(
            normalized,
            listOf(ParticipantStatus.CONFIRMED, ParticipantStatus.PENDING_PAYMENT, ParticipantStatus.WAITLIST)
        ).filter { it.role.hasAuthority(com.bojogar.bot.enums.ParticipantRole.ADMIN) }

        return managed.sumOf { participant ->
            getWalletBalance(participant.pelada.codigo)
        }
    }
}
