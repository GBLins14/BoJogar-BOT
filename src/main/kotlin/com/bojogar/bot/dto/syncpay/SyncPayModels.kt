package com.bojogar.bot.dto.syncpay

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayAuthRequest(
    val clientId: String,
    val clientSecret: String
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayAuthResponse(
    val accessToken: String,
    val tokenType: String? = null,
    val expiresIn: Int? = null,
    val expiresAt: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayCashInRequest(
    val amount: Double,
    val description: String?,
    val webhookUrl: String,
    val client: SyncPayClientInfo
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayClientInfo(
    val name: String,
    val cpf: String,
    val email: String,
    val phone: String
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayCashInResponse(
    val message: String?,
    val pixCode: String?,
    val identifier: String?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayTransactionResponse(
    val referenceId: String?,
    val currency: String?,
    val amount: Double?,
    val transactionDate: String?,
    val status: String?,
    val description: String?,
    val pixCode: String?
)

data class SyncPayWebhookPayload(
    val data: SyncPayWebhookData?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayWebhookData(
    val id: String?,
    val endToEnd: String?,
    val client: SyncPayWebhookClient?,
    val debtorAccount: SyncPayDebtorAccount?,
    val pixCode: String?,
    val amount: Double?,
    val finalAmount: Double?,
    val currency: String?,
    val status: String?,
    val paymentMethod: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class SyncPayWebhookClient(
    val name: String?,
    val email: String?,
    val document: String?
)

data class SyncPayDebtorAccount(
    val name: String?,
    val document: String?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayCashOutRequest(
    val amount: Double,
    val pixKey: String,
    val pixKeyType: String,
    val description: String?,
    val webhookUrl: String
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SyncPayCashOutResponse(
    val message: String?,
    val identifier: String?,
    val status: String?
)
