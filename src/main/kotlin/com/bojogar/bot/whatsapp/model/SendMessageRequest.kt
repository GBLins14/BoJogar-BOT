package com.bojogar.bot.whatsapp.model

data class SendMessageRequest(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "text",
    val text: TextContent? = null
)

data class TextContent(
    val body: String,
    val preview_url: Boolean = false
)
