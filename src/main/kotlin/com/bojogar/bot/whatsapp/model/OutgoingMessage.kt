package com.bojogar.bot.whatsapp.model

data class MessagePayload(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String,
    val text: TextBody? = null,
    val interactive: InteractiveBody? = null
)

data class TextBody(
    val body: String,
    val preview_url: Boolean? = null
)

data class InteractiveBody(
    val type: String,
    val header: InteractiveHeader? = null,
    val body: InteractiveTextBody,
    val footer: InteractiveTextBody? = null,
    val action: InteractiveAction
)

data class InteractiveHeader(
    val type: String = "text",
    val text: String
)

data class InteractiveTextBody(
    val text: String
)

data class InteractiveAction(
    val buttons: List<InteractiveButton>? = null,
    val button: String? = null,
    val sections: List<InteractiveSectionBody>? = null
)

data class InteractiveButton(
    val type: String = "reply",
    val reply: InteractiveReply
)

data class InteractiveReply(
    val id: String,
    val title: String
)

data class InteractiveSectionBody(
    val title: String,
    val rows: List<InteractiveRowBody>
)

data class InteractiveRowBody(
    val id: String,
    val title: String,
    val description: String? = null
)

data class MarkAsReadPayload(
    val messaging_product: String = "whatsapp",
    val status: String = "read",
    val message_id: String
)
