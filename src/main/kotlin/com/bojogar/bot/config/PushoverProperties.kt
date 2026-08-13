package com.bojogar.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pushover")
data class PushoverProperties(
    val token: String = "",
    val userKey: String = ""
)
