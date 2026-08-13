package com.bojogar.bot.dto.abacatepay

data class AbacatePayTransparentRequest(
    val method: String = "PIX",
    val data: AbacatePayTransparentData
)

data class AbacatePayTransparentData(
    val amount: Int,
    val description: String? = null,
    val expiresIn: Int? = null,
    val customer: AbacatePayCustomer? = null,
    val metadata: Map<String, String>? = null
)

data class AbacatePayCustomer(
    val name: String,
    val email: String,
    val taxId: String,
    val cellphone: String
)

data class AbacatePayEnvelope<T>(
    val data: T?,
    val success: Boolean? = null,
    val error: String? = null
)

data class AbacatePayTransparentResponse(
    val id: String?,
    val brCode: String?,
    val brCodeBase64: String?,
    val amount: Int?,
    val status: String?,
    val metadata: Map<String, Any>? = null
)

data class AbacatePayWebhookPayload(
    val event: String?,
    val data: AbacatePayWebhookData?
)

data class AbacatePayWebhookData(
    val id: String?,
    val amount: Int?,
    val status: String?,
    val brCode: String?,
    val metadata: Map<String, Any>? = null
)
