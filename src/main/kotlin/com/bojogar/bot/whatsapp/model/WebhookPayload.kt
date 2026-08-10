package com.bojogar.bot.whatsapp.model

import com.fasterxml.jackson.annotation.JsonProperty

data class WebhookPayload(
    @JsonProperty("object")
    val objectType: String = "",
    val entry: List<WebhookEntry> = emptyList()
)

data class WebhookEntry(
    val id: String = "",
    val changes: List<WebhookChange> = emptyList()
)

data class WebhookChange(
    val value: WebhookValue? = null,
    val field: String = ""
)

data class WebhookValue(
    @JsonProperty("messaging_product")
    val messagingProduct: String = "",
    val metadata: WebhookMetadata? = null,
    val contacts: List<WebhookContact> = emptyList(),
    val messages: List<IncomingMessage> = emptyList()
)

data class WebhookMetadata(
    @JsonProperty("display_phone_number")
    val displayPhoneNumber: String = "",
    @JsonProperty("phone_number_id")
    val phoneNumberId: String = ""
)

data class WebhookContact(
    val profile: WebhookProfile? = null,
    @JsonProperty("wa_id")
    val waId: String = ""
)

data class WebhookProfile(
    val name: String = ""
)

data class IncomingMessage(
    val from: String = "",
    val id: String = "",
    val timestamp: String = "",
    val text: MessageText? = null,
    val type: String = ""
)

data class MessageText(
    val body: String = ""
)
