package com.bojogar.bot.controller

import com.bojogar.bot.config.WhatsAppProperties
import com.bojogar.bot.whatsapp.handler.MessageHandler
import com.bojogar.bot.whatsapp.model.WebhookPayload
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/api/webhook")
class WebhookController(
    private val messageHandler: MessageHandler,
    private val properties: WhatsAppProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(WebhookController::class.java)
    }

    @GetMapping
    fun verify(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") token: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        log.info("Webhook verification — mode: {}", mode)
        if (mode == "subscribe" && token == properties.verifyToken) {
            log.info("Webhook verified successfully")
            return ResponseEntity.ok(challenge)
        }
        log.warn("Webhook verification failed — invalid token")
        return ResponseEntity.status(403).build()
    }

    @PostMapping
    fun receive(@RequestBody payload: WebhookPayload): ResponseEntity<Void> {
        log.info("Webhook received — {} entries", payload.entry.size)
        try {
            messageHandler.handle(payload)
        } catch (e: Exception) {
            log.error(">>> ERRO ao processar webhook: {}", e.message, e)
        }
        return ResponseEntity.ok().build()
    }
}
