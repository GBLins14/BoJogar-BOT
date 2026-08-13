package com.bojogar.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bojogar.admin")
data class AdminProperties(
    val phone: String = ""
)
