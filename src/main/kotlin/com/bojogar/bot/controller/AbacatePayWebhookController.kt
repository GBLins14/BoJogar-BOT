package com.bojogar.bot.controller

import com.bojogar.bot.config.AbacatePayProperties
import com.bojogar.bot.dto.abacatepay.AbacatePayWebhookPayload
import com.bojogar.bot.service.PagamentoService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/v1/api/abacatepay/webhook")
class AbacatePayWebhookController(
    private val pagamentoService: PagamentoService,
    private val abacatePayProperties: AbacatePayProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(AbacatePayWebhookController::class.java)
        private val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
    }

    @PostMapping
    fun handleWebhook(
        @RequestHeader("X-Webhook-Signature", required = false) signature: String?,
        @RequestBody body: String
    ): ResponseEntity<Void> {
        log.info("AbacatePay webhook received")

        if (abacatePayProperties.webhookSecret.isNotBlank()) {
            if (signature.isNullOrBlank() || !verifyHmac(body, signature)) {
                log.warn("Invalid webhook signature from AbacatePay")
                return ResponseEntity.status(401).build()
            }
        }

        try {
            val payload = mapper.readValue(body, AbacatePayWebhookPayload::class.java)

            val event = payload.event
            val data = payload.data

            if (data == null) {
                log.warn("Webhook payload has no data")
                return ResponseEntity.ok().build()
            }

            log.info("Processing webhook event: {} - id: {}", event, data.id)

            when (event) {
                "transparent.completed" -> {
                    if (data.id != null) {
                        pagamentoService.processWebhookPayment(data.id, null)
                    }
                }
                "transparent.refunded" -> {
                    if (data.id != null) {
                        pagamentoService.processWebhookRefund(data.id)
                    }
                }
                "transparent.disputed" -> {
                    log.warn("Transparent payment DISPUTED - id: {}, amount: {}", data.id, data.amount)
                }
                "transparent.lost" -> {
                    log.warn("Transparent payment LOST - id: {}", data.id)
                }
                "checkout.completed" -> {
                    log.info("Checkout completed - id: {}", data.id)
                }
                "checkout.refunded" -> {
                    log.info("Checkout refunded - id: {}", data.id)
                }
                "checkout.disputed" -> {
                    log.warn("Checkout DISPUTED - id: {}, amount: {}", data.id, data.amount)
                }
                "checkout.lost" -> {
                    log.info("Checkout lost - id: {}", data.id)
                }
                "payout.completed" -> {
                    log.info("Payout completed - id: {}", data.id)
                }
                "payout.failed" -> {
                    log.warn("Payout FAILED - id: {}, amount: {}", data.id, data.amount)
                }
                "transfer.completed" -> {
                    log.info("Transfer completed - id: {}", data.id)
                }
                "transfer.failed" -> {
                    log.warn("Transfer FAILED - id: {}, amount: {}", data.id, data.amount)
                }
                else -> {
                    log.info("Unhandled webhook event: {} - id: {}", event, data.id)
                }
            }
        } catch (e: Exception) {
            log.error("Error processing AbacatePay webhook: {}", e.message, e)
        }

        return ResponseEntity.ok().build()
    }

    private fun verifyHmac(payload: String, receivedSignature: String): Boolean {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(abacatePayProperties.webhookSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val computed = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
            MessageDigest.isEqual(computed.toByteArray(), receivedSignature.toByteArray())
        } catch (e: Exception) {
            log.error("Error verifying HMAC: {}", e.message)
            false
        }
    }
}
