package com.bojogar.bot.controller

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
    private val pagamentoService: PagamentoService
) {

    companion object {
        private val log = LoggerFactory.getLogger(AbacatePayWebhookController::class.java)
        private val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        private const val ABACATEPAY_PUBLIC_KEY =
            "t9dXRhHHo3yDEj5pVDYz0frf7q6bMKyMRmxxCPIPp3RCplBfXRxqlC6ZpiWmOqj4L63qEaeUOtrCI8P0VMUgo6iIga2ri9ogaHFs0WIIywSMg0q7RmBfybe1E5XJcfC4IW3alNqym0tXoAKkzvfEjZxV6bE0oG2zJrNNYmUCKZyV0KZ3JS8Votf9EAWWYdiDkMkpbMdPggfh1EqHlVkMiTady6jOR3hyzGEHrIz2Ret0xHKMbiqkr9HS1JhNHDX9"
    }

    @PostMapping
    fun handleWebhook(
        @RequestHeader("X-Webhook-Signature", required = false) signature: String?,
        @RequestBody body: String
    ): ResponseEntity<Void> {
        log.info("AbacatePay webhook received - body: {}", body)

        if (signature.isNullOrBlank() || !verifyHmac(body, signature)) {
            log.warn("Invalid webhook signature from AbacatePay")
            return ResponseEntity.status(401).build()
        }

        try {
            val payload = mapper.readValue(body, AbacatePayWebhookPayload::class.java)

            val event = payload.event
            val data = payload.data

            if (data == null) {
                log.warn("Webhook payload has no data")
                return ResponseEntity.ok().build()
            }

            val transparent = data.transparent

            when (event) {
                "transparent.completed" -> {
                    if (transparent?.id != null) {
                        log.info("Processing transparent.completed - id: {}", transparent.id)
                        pagamentoService.processWebhookPayment(transparent.id, transparent.endToEndIdentifier)
                    } else {
                        log.warn("transparent.completed received but no transparent data found")
                    }
                }
                "transparent.refunded" -> {
                    if (transparent?.id != null) {
                        log.info("Processing transparent.refunded - id: {}", transparent.id)
                        pagamentoService.processWebhookRefund(transparent.id)
                    } else {
                        log.warn("transparent.refunded received but no transparent data found")
                    }
                }
                "transparent.disputed" -> {
                    log.warn("Transparent payment DISPUTED - id: {}, amount: {}", transparent?.id, transparent?.amount)
                }
                "transparent.lost" -> {
                    log.warn("Transparent payment LOST - id: {}", transparent?.id)
                }
                else -> {
                    log.info("Webhook event received: {}", event)
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
            mac.init(SecretKeySpec(ABACATEPAY_PUBLIC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val computed = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
            MessageDigest.isEqual(computed.toByteArray(), receivedSignature.toByteArray())
        } catch (e: Exception) {
            log.error("Error verifying HMAC: {}", e.message)
            false
        }
    }
}
