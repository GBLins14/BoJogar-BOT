package com.bojogar.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "abacatepay")
data class AbacatePayProperties(
    val apiKey: String = "",
    val webhookSecret: String = "",
    val platformFeePercent: Int = 10
)
