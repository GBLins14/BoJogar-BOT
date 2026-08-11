package com.bojogar.bot.service

import com.bojogar.bot.config.SyncPayProperties
import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.dto.syncpay.SyncPayClientInfo
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
    private val syncPayClient: SyncPayClient,
    private val syncPayProperties: SyncPayProperties,
    private val notificationService: NotificationService
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
        val participant = payment.participant
        val pelada = participant.pelada

        val requesterParticipant = participantRepository.findByUserPhoneAndPeladaCodigo(requesterNormalized, pelada.codigo)
        if (requesterParticipant == null || !requesterParticipant.role.hasAuthority(com.bojogar.bot.enums.ParticipantRole.ADMIN)) {
            throw BusinessException("Sem permissao para confirmar pagamento")
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
            ?: return PixGenerationResult.Error("Pagamento pendente nao encontrado")

        // If PIX already generated, return existing code
        if (payment.pixCode != null && payment.syncpayIdentifier != null) {
            return PixGenerationResult.Success(payment.pixCode!!, payment.id!!)
        }

        return try {
            val pelada = payment.participant.pelada
            val clientInfo = SyncPayClientInfo(
                name = userName,
                cpf = userCpf,
                email = userEmail,
                phone = userPhone.takeLast(11)
            )

            val response = syncPayClient.generatePix(
                amount = payment.valor,
                description = "Pelada ${pelada.codigo} - ${pelada.esporte.label}",
                clientInfo = clientInfo
            )

            if (response.pixCode != null && response.identifier != null) {
                payment.pixCode = response.pixCode
                payment.syncpayIdentifier = response.identifier
                pagamentoRepository.save(payment)

                log.info("PIX generated for participant {} in pelada {} - identifier: {}",
                    participantId, pelada.codigo, response.identifier)
                PixGenerationResult.Success(response.pixCode, payment.id!!)
            } else {
                log.error("SyncPay returned empty pixCode or identifier")
                PixGenerationResult.Error("Erro ao gerar PIX. Tente novamente.")
            }
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
        val peladaEsporteLabel = pelada.esporte.label
        val chavePix = pelada.chavePix
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

        // Transfer to admin (amount minus platform fee)
        if (!chavePix.isNullOrBlank()) {
            val feePercent = BigDecimal(syncPayProperties.platformFeePercent)
            val fee = amount.multiply(feePercent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            val transferAmount = amount.subtract(fee)

            if (transferAmount > BigDecimal.ZERO) {
                val pixKeyType = detectPixKeyType(chavePix)
                try {
                    val response = syncPayClient.cashOut(
                        amount = transferAmount,
                        pixKey = chavePix,
                        pixKeyType = pixKeyType,
                        description = "Repasse pelada $peladaCode - $peladaEsporteLabel"
                    )
                    log.info("Transfer initiated for pelada {} - amount: {} (fee: {}), identifier: {}",
                        peladaCode, transferAmount, fee, response.identifier)
                } catch (e: Exception) {
                    log.error("Failed to transfer to admin for pelada {} - amount: {}: {}",
                        peladaCode, transferAmount, e.message, e)
                }
            } else {
                log.warn("Transfer amount is zero or negative after fee for pelada {} - skipping", peladaCode)
            }
        } else {
            log.warn("No PIX key configured for pelada {} - skipping transfer", peladaCode)
        }

        log.info("Payment confirmed via webhook for participant {} in pelada {}", participantPhone, peladaCode)
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

    private fun detectPixKeyType(pixKey: String): String {
        val cleaned = pixKey.replace(Regex("[^a-zA-Z0-9@.+-]"), "")
        return when {
            cleaned.matches(Regex("^\\d{11}$")) -> "cpf"
            cleaned.matches(Regex("^\\d{14}$")) -> "cnpj"
            cleaned.contains("@") -> "email"
            cleaned.matches(Regex("^\\+?\\d{10,13}$")) -> "phone"
            else -> "random"
        }
    }
}
