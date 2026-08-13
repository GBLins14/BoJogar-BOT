package com.bojogar.bot.service

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.whatsapp.UxCopy
import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
            append("Uma vaga abriu na pelada *${pelada.codigo}* \u2014 ${pelada.esporteLabel}!\n\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n\n")
            append("Você foi *automaticamente confirmado*!")
            if (pelada.valorPorJogador > BigDecimal.ZERO) {
                append("\n\n\uD83D\uDCB0 *Valor:* ${UxCopy.formatPrice(pelada.valorPorJogador)}")
            }
        }

        try {
            whatsappService.sendMessage(promoted.userPhone, message)
            if (pelada.valorPorJogador > BigDecimal.ZERO) {
                whatsappService.sendButtons(
                    to = promoted.userPhone,
                    body = "Pague agora para garantir sua vaga!",
                    buttons = listOf(
                        Button(id = "/pagar gerar ${pelada.codigo}", title = "Pagar via PIX"),
                        Button(id = "/start", title = "Menu")
                    )
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to notify waitlist promotion {}: {}", promoted.userPhone, e.message)
        }
    }

    @Async
    fun notifyPeladaCancelled(pelada: PeladaResponse, participants: List<ParticipantResponse>) {
        val message = buildString {
            append("\u274C *Pelada Cancelada*\n\n")
            append("A pelada *${pelada.codigo}* \u2014 ${pelada.esporteLabel} foi cancelada pelo organizador.\n\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n\n")
            append("Caso tenha direito a reembolso, entre em contato com o organizador.")
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
            append("Você foi removido da pelada *${pelada.codigo}* \u2014 ${pelada.esporteLabel} pelo organizador.\n\n")
            append("Caso tenha dúvidas, entre em contato com o organizador.")
        }

        notifyUser(removed.userPhone, message)
    }

    @Async
    fun notifyPaymentConfirmed(participantPhone: String, participantName: String, pelada: PeladaResponse) {
        val message = buildString {
            append("\u2705 *Pagamento Confirmado \u2014 Vaga Garantida!*\n\n")
            append("Seu pagamento de *${UxCopy.formatPrice(pelada.valorPorJogador)}* para a pelada *${pelada.codigo}* foi confirmado.\n\n")
            append("\uD83C\uDFC6 ${pelada.esporteLabel}\n")
            append("\uD83D\uDCCD ${pelada.local}\n")
            append("\uD83D\uDCC5 ${UxCopy.formatDate(pelada.dataHora)}\n\n")
            append("Sua vaga está garantida! Te vemos lá, $participantName! \uD83D\uDCAA")
        }

        notifyUser(participantPhone, message)
    }

    @Async
    fun notifyPaymentRefunded(participantPhone: String, participantName: String, pelada: PeladaResponse, amount: BigDecimal) {
        val message = buildString {
            append("\u26A0\uFE0F *Pagamento Estornado*\n\n")
            append("Seu pagamento de *${UxCopy.formatPrice(amount)}* na pelada *${pelada.codigo}* foi estornado.\n\n")
            append("Sua inscri\u00E7\u00E3o voltou para pendente de pagamento.")
        }

        notifyUser(participantPhone, message)

        val admins = participantService.getActiveParticipants(pelada.codigo)
            .filter { it.role in listOf("OWNER", "ADMIN") }

        val adminMessage = buildString {
            append("\u26A0\uFE0F *Estorno Recebido*\n\n")
            append("O pagamento de *${UxCopy.formatPrice(amount)}* de *$participantName* na pelada *${pelada.codigo}* foi estornado.")
        }

        admins.forEach { admin ->
            try {
                whatsappService.sendMessage(admin.userPhone, adminMessage)
            } catch (e: Exception) {
                log.warn("Failed to notify admin about refund {}: {}", admin.userPhone, e.message)
            }
        }
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
