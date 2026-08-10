package com.bojogar.bot.service

import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.entity.PeladaParticipant
import com.bojogar.bot.enums.ParticipantStatus
import com.bojogar.bot.repository.PeladaParticipantRepository
import com.bojogar.bot.repository.PeladaRepository
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class NotificationService(
    private val whatsappService: WhatsAppService,
    private val participantRepository: PeladaParticipantRepository,
    private val peladaRepository: PeladaRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }

    @Async
    fun notifyParticipants(peladaCode: String, message: String) {
        val pelada = peladaRepository.findByCodigo(peladaCode.uppercase()) ?: return
        val confirmed = participantRepository.findByPeladaIdAndStatus(pelada.id!!, ParticipantStatus.CONFIRMED)

        confirmed.forEach { p ->
            try {
                whatsappService.sendMessage(p.user.phone, message)
            } catch (e: Exception) {
                log.warn("Failed to notify {}: {}", p.user.phone, e.message)
            }
        }

        log.info("Notified {} participants of pelada {}", confirmed.size, peladaCode)
    }

    @Async
    fun notifyUser(phone: String, message: String) {
        try {
            whatsappService.sendMessage(phone, message)
        } catch (e: Exception) {
            log.warn("Failed to notify {}: {}", phone, e.message)
        }
    }

    @Async
    fun notifyWaitlistPromotion(participant: PeladaParticipant, pelada: Pelada) {
        val message = buildString {
            append("\uD83C\uDF89 *Vaga Liberada!*\n\n")
            append("Uma vaga abriu na pelada *${pelada.codigo}* — ${pelada.esporte.label}!\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n\n")
            append("Voce foi *automaticamente confirmado*!")
            if (pelada.valorPorJogador > java.math.BigDecimal.ZERO) {
                append("\n\n\uD83D\uDCB0 Valor: R$ ${pelada.valorPorJogador}")
                if (!pelada.chavePix.isNullOrBlank()) {
                    append("\n\uD83D\uDCF2 Pix: ${pelada.chavePix}")
                }
            }
        }

        notifyUser(participant.user.phone, message)
    }

    @Async
    fun notifyPeladaCancelled(pelada: Pelada, participants: List<PeladaParticipant>) {
        val message = buildString {
            append("\u274C *Pelada Cancelada*\n\n")
            append("A pelada *${pelada.codigo}* — ${pelada.esporte.label} foi cancelada pelo organizador.\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora.format(DATE_FMT)}\n\n")
            append("Caso tenha direito a reembolso, o organizador entrara em contato.")
        }

        participants.forEach { p ->
            if (p.user.phone != pelada.createdBy.phone) {
                notifyUser(p.user.phone, message)
            }
        }

        log.info("Notified {} participants about cancellation of pelada {}", participants.size, pelada.codigo)
    }

    @Async
    fun notifyParticipantRemoved(participant: PeladaParticipant, pelada: Pelada) {
        val message = buildString {
            append("\u26A0\uFE0F *Removido da Pelada*\n\n")
            append("Voce foi removido da pelada *${pelada.codigo}* — ${pelada.esporte.label} pelo organizador.\n")
            append("Caso tenha duvidas, entre em contato com o organizador.")
        }

        notifyUser(participant.user.phone, message)
    }
}
