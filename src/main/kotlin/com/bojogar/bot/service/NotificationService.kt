package com.bojogar.bot.service

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Service
class NotificationService(
    private val whatsappService: WhatsAppService,
    private val participantService: ParticipantService
) {

    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
    }

    @Async
    fun notifyParticipants(peladaCode: String, message: String) {
        val participants = participantService.getActiveParticipants(peladaCode)
            .filter { it.status == "CONFIRMED" }

        participants.forEach { p ->
            try {
                whatsappService.sendMessage(p.userPhone, message)
            } catch (e: Exception) {
                log.warn("Failed to notify {}: {}", p.userPhone, e.message)
            }
        }

        log.info("Notified {} participants of pelada {}", participants.size, peladaCode)
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
    fun notifyWaitlistPromotion(promoted: ParticipantResponse, pelada: PeladaResponse) {
        val message = buildString {
            append("\uD83C\uDF89 *Vaga Liberada!*\n\n")
            append("Uma vaga abriu na pelada *${pelada.codigo}* — ${pelada.esporteLabel}!\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora}\n\n")
            append("Voce foi *automaticamente confirmado*!")
            if (pelada.valorPorJogador > BigDecimal.ZERO) {
                append("\n\n\uD83D\uDCB0 *Valor:* R$ ${pelada.valorPorJogador}")
                append("\n\nEnvie */pagar* para gerar o PIX e efetuar o pagamento.")
            }
        }

        notifyUser(promoted.userPhone, message)
    }

    @Async
    fun notifyPeladaCancelled(pelada: PeladaResponse, participants: List<ParticipantResponse>) {
        val message = buildString {
            append("\u274C *Pelada Cancelada*\n\n")
            append("A pelada *${pelada.codigo}* — ${pelada.esporteLabel} foi cancelada pelo organizador.\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora}\n\n")
            append("Caso tenha direito a reembolso, o organizador entrara em contato.")
        }

        participants
            .filter { it.userPhone != pelada.createdByPhone }
            .forEach { notifyUser(it.userPhone, message) }

        log.info("Notified {} participants about cancellation of pelada {}", participants.size, pelada.codigo)
    }

    @Async
    fun notifyParticipantRemoved(removed: ParticipantResponse, pelada: PeladaResponse) {
        val message = buildString {
            append("\u26A0\uFE0F *Removido da Pelada*\n\n")
            append("Voce foi removido da pelada *${pelada.codigo}* — ${pelada.esporteLabel} pelo organizador.\n")
            append("Caso tenha duvidas, entre em contato com o organizador.")
        }

        notifyUser(removed.userPhone, message)
    }

    @Async
    fun notifyPaymentConfirmed(participantPhone: String, participantName: String, pelada: PeladaResponse) {
        val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val message = buildString {
            append("\u2705 *Pagamento Confirmado!*\n\n")
            append("Seu pagamento de *R$ ${pelada.valorPorJogador}* para a pelada *${pelada.codigo}* foi confirmado.\n\n")
            append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${pelada.dataHora.format(dateFmt)}\n\n")
            append("Te vejo la, $participantName!")
        }

        notifyUser(participantPhone, message)
    }

    @Async
    fun notifyAdminPaymentReceived(peladaCode: String, participantName: String, amount: BigDecimal) {
        val admins = participantService.getActiveParticipants(peladaCode)
            .filter { it.role in listOf("OWNER", "ADMIN") }

        val message = buildString {
            append("\uD83D\uDCB0 *Pagamento Recebido*\n\n")
            append("*$participantName* pagou *R$ $amount* na pelada *$peladaCode*.")
        }

        admins.forEach { admin ->
            try {
                whatsappService.sendMessage(admin.userPhone, message)
            } catch (e: Exception) {
                log.warn("Failed to notify admin {}: {}", admin.userPhone, e.message)
            }
        }
    }
}
