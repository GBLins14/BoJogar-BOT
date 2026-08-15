package com.bojogar.bot.dto.abacatepay

data class AbacatePayTransparentRequest(
    val method: String = "PIX",
    val data: AbacatePayTransparentData
)

data class AbacatePayTransparentData(
    val amount: Int,
    val description: String? = null,
    val expiresIn: Int? = null,
    val customer: AbacatePayCustomer? = null
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
    val transparent: AbacatePayWebhookItem? = null,
    val checkout: AbacatePayWebhookItem? = null,
    val payout: AbacatePayWebhookItem? = null,
    val transfer: AbacatePayWebhookItem? = null
)

data class AbacatePayWebhookItem(
    val id: String?,
    val amount: Int?,
    val status: String?,
    val endToEndIdentifier: String? = null
)

data class AbacatePayStoreResponse(
    val id: String?,
    val name: String?,
    val balance: AbacatePayStoreBalance?
)

data class AbacatePayStoreBalance(
    val available: Long = 0,
    val pending: Long = 0,
    val blocked: Long = 0
)

data class AbacatePayPayoutRequest(
    val amount: Int,
    val externalId: String,
    val description: String? = null
)

data class AbacatePayPayoutResponse(
    val id: String?,
    val status: String?,
    val devMode: Boolean? = null,
    val receiptUrl: String? = null,
    val amount: Int?,
    val platformFee: Int? = null,
    val externalId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
