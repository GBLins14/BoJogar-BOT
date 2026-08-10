package com.bojogar.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "whatsapp.api")
data class WhatsAppProperties(
    val token: String,
    val phoneNumberId: String,
    val verifyToken: String,
    val apiVersion: String = "v22.0"
)
