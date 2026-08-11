package com.bojogar.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "syncpay")
data class SyncPayProperties(
    val baseUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val webhookUrl: String = "",
    val webhookToken: String = "",
    val platformFeePercent: Int = 10
)
