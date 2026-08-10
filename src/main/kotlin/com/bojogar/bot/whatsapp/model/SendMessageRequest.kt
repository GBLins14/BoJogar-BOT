package com.bojogar.bot.whatsapp.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SendMessageRequest(
    @JsonProperty("messaging_product")
    val messagingProduct: String = "whatsapp",
    @JsonProperty("recipient_type")
    val recipientType: String = "individual",
    val to: String,
    val type: String = "text",
    val text: TextContent? = null
)

data class TextContent(
    val body: String,
    @JsonProperty("preview_url")
    val previewUrl: Boolean = false
)
