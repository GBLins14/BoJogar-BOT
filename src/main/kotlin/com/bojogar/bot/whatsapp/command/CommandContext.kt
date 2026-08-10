package com.bojogar.bot.whatsapp.command

data class CommandContext(
    val from: String,
    val senderName: String,
    val messageId: String,
    val args: List<String>,
    val rawMessage: String
)
