package com.bojogar.bot.service

import com.bojogar.bot.config.PushoverProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class PushoverClient(
    private val properties: PushoverProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(PushoverClient::class.java)
        private const val API_URL = "https://api.pushover.net/1/messages.json"
        private val MOTIVATIONAL_MESSAGES = listOf(
            "Cada venda é prova de que você não desistiu. Segue voando! 🚀",
            "Anos de luta, e agora o jogo virou. Você merece cada centavo! 💪",
            "O digital finalmente tá pagando. Isso é só o começo! 🔥",
            "Mais uma venda, mais uma prova de que persistência vence. 👊",
            "Enquanto muitos desistiram, você continuou. Tá colhendo agora! 🌱",
            "Dinheiro entrando enquanto você dorme. Esse é o poder do digital! 💰",
            "Quem planta todo dia, colhe sem parar. Bora! 🏆",
            "Essa notificação é o som da liberdade financeira chegando! 🎶",
            "Mais uma! O sistema tá rodando e você tá crescendo! 📈",
            "Você construiu isso do zero. Orgulho define. 🏗️",
            "De tentativa em tentativa até o sucesso. Chegou a hora! ⭐",
            "A máquina tá ligada e não para mais. Bora escalar! ⚡",
            "Lembra quando era só sonho? Agora é notificação de venda! 🎯",
            "Resiliência > talento. Você provou isso. 💎",
            "Mais uma venda. Mais um passo pro próximo nível. 🪜",
        )
    }

    fun isConfigured(): Boolean = properties.token.isNotBlank() && properties.userKey.isNotBlank()

    fun notifySale(amount: BigDecimal) {
        send("Venda aprovada! 🎉", "R$ $amount\n\n${MOTIVATIONAL_MESSAGES.random()}")
    }

    fun send(title: String, message: String, priority: Int = 0) {
        if (!isConfigured()) {
            log.debug("Pushover not configured, skipping notification")
            return
        }

        try {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("token", properties.token)
                add("user", properties.userKey)
                add("title", title)
                add("message", message)
                add("priority", priority.toString())
            }

            RestClient.builder().build()
                .post()
                .uri(API_URL)
                .body(form)
                .retrieve()
                .toBodilessEntity()

            log.info("Pushover notification sent: {}", title)
        } catch (e: Exception) {
            log.error("Failed to send Pushover notification: {}", e.message)
        }
    }
}
