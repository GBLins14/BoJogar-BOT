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

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun verify(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") token: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        if (mode == "subscribe" && token == properties.verifyToken) {
            log.info("Webhook verificado com sucesso")
            return ResponseEntity.ok(challenge)
        }
        log.warn("Falha na verificacao do webhook")
        return ResponseEntity.status(403).build()
    }

    @PostMapping
    fun receive(@RequestBody payload: WebhookPayload): ResponseEntity<Void> {
        messageHandler.handle(payload)
        return ResponseEntity.ok().build()
    }
}
