package com.bojogar.bot.controller

import com.bojogar.bot.config.SyncPayProperties
import com.bojogar.bot.dto.syncpay.SyncPayWebhookPayload
import com.bojogar.bot.service.PagamentoService
import com.bojogar.bot.service.NotificationService
import com.bojogar.bot.service.PeladaService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/api/syncpay/webhook")
class SyncPayWebhookController(
    private val pagamentoService: PagamentoService,
    private val notificationService: NotificationService,
    private val peladaService: PeladaService,
    private val syncPayProperties: SyncPayProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(SyncPayWebhookController::class.java)
    }

    @PostMapping
    fun handleWebhook(
        @RequestHeader("event", required = false) event: String?,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody payload: SyncPayWebhookPayload
    ): ResponseEntity<Void> {
        log.info("SyncPay webhook received - event: {}, id: {}", event, payload.data?.id)

        // Validate webhook token
        if (syncPayProperties.webhookToken.isNotBlank()) {
            val expectedToken = "Bearer ${syncPayProperties.webhookToken}"
            if (authorization != expectedToken) {
                log.warn("Invalid webhook authorization header")
                return ResponseEntity.ok().build()
            }
        }

        try {
            val data = payload.data ?: run {
                log.warn("Webhook payload has no data")
                return ResponseEntity.ok().build()
            }

            if (data.status == "completed" && data.id != null) {
                log.info("Processing completed payment - identifier: {}", data.id)

                val pagamento = pagamentoService.confirmPaymentByWebhook(
                    syncpayIdentifier = data.id,
                    endToEnd = data.endToEnd
                )

                if (pagamento != null) {
                    val participant = pagamento.participant
                    val pelada = peladaService.findByCode(participant.pelada.codigo)

                    if (pelada != null) {
                        notificationService.notifyPaymentConfirmed(
                            participantPhone = participant.user.phone,
                            participantName = participant.displayName ?: participant.user.name,
                            pelada = pelada
                        )
                        notificationService.notifyAdminPaymentReceived(
                            peladaCode = pelada.codigo,
                            participantName = participant.displayName ?: participant.user.name,
                            amount = pagamento.valor
                        )
                    }

                    // Transfer to admin (amount minus platform fee)
                    pagamentoService.transferToAdmin(pagamento)

                    log.info("Payment confirmed via webhook for participant {} in pelada {}",
                        participant.user.phone, participant.pelada.codigo)
                } else {
                    log.warn("No matching pending payment found for identifier: {}", data.id)
                }
            } else {
                log.info("Ignoring webhook - status: {}, id: {}", data.status, data.id)
            }
        } catch (e: Exception) {
            log.error("Error processing SyncPay webhook: {}", e.message, e)
        }

        // Always return 200 to prevent retries
        return ResponseEntity.ok().build()
    }
}
