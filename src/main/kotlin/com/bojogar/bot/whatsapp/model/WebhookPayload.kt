package com.bojogar.bot.whatsapp.model

data class WebhookPayload(
    val entry: List<WebhookEntry> = emptyList()
)

data class WebhookEntry(
    val id: String? = null,
    val changes: List<WebhookChange> = emptyList()
)

data class WebhookChange(
    val value: WebhookValue,
    val field: String? = null
)

data class WebhookValue(
    val messaging_product: String? = null,
    val metadata: WebhookMetadata? = null,
    val contacts: List<WebhookContact>? = null,
    val messages: List<IncomingMessage>? = null
)

data class WebhookMetadata(
    val display_phone_number: String? = null,
    val phone_number_id: String? = null
)

data class WebhookContact(
    val profile: WebhookProfile? = null,
    val wa_id: String? = null
)

data class WebhookProfile(
    val name: String? = null
)

data class IncomingMessage(
    val from: String,
    val id: String,
    val timestamp: String? = null,
    val text: MessageText? = null,
    val type: String? = null
)

data class MessageText(
    val body: String? = null
)
