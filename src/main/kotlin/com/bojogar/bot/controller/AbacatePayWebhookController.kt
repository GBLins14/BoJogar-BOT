package com.bojogar.bot.controller

import com.bojogar.bot.config.AbacatePayProperties
import com.bojogar.bot.dto.abacatepay.AbacatePayWebhookPayload
import com.bojogar.bot.service.PagamentoService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
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
    }

    @PostMapping
    fun handleWebhook(
        @RequestHeader("x-abacatepay-signature", required = false) signature: String?,
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
            val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
            val payload = mapper.readValue(body, AbacatePayWebhookPayload::class.java)

            val event = payload.event
            val data = payload.data

            if (data == null) {
                log.warn("Webhook payload has no data")
                return ResponseEntity.ok().build()
            }

            if (event == "transparent.completed" && data.id != null) {
                log.info("Processing completed transparent payment - id: {}", data.id)
                pagamentoService.processWebhookPayment(data.id, null)
            } else {
                log.info("Ignoring webhook - event: {}, id: {}", event, data.id)
            }
        } catch (e: Exception) {
            log.error("Error processing AbacatePay webhook: {}", e.message, e)
        }

        return ResponseEntity.ok().build()
    }

    private fun verifyHmac(payload: String, receivedSignature: String): Boolean {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(abacatePayProperties.webhookSecret.toByteArray(), "HmacSHA256"))
            val computed = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
            computed.equals(receivedSignature, ignoreCase = true)
        } catch (e: Exception) {
            log.error("Error verifying HMAC: {}", e.message)
            false
        }
    }
}
