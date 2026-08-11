package com.bojogar.bot.service

import com.bojogar.bot.config.SyncPayProperties
import com.bojogar.bot.dto.syncpay.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant

@Component
class SyncPayClient(
    @Qualifier("syncPayRestClient") private val restClient: RestClient,
    private val properties: SyncPayProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(SyncPayClient::class.java)
        private const val TOKEN_REFRESH_MARGIN_SECONDS = 60L
    }

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Instant = Instant.EPOCH

    fun generatePix(amount: BigDecimal, description: String?, clientInfo: SyncPayClientInfo): SyncPayCashInResponse {
        val token = getValidToken()

        val request = SyncPayCashInRequest(
            amount = amount.toDouble(),
            description = description,
            webhookUrl = properties.webhookUrl,
            client = clientInfo
        )

        log.info("Generating PIX for {} - amount: {}", clientInfo.cpf, amount)

        val response = restClient.post()
            .uri("/api/partner/v1/cash-in")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .body(request)
            .retrieve()
            .body(SyncPayCashInResponse::class.java)
            ?: throw RuntimeException("Empty response from SyncPay cash-in")

        log.info("PIX generated - identifier: {}", response.identifier)
        return response
    }

    fun cashOut(amount: BigDecimal, pixKey: String, pixKeyType: String, description: String?): SyncPayCashOutResponse {
        val token = getValidToken()

        val request = SyncPayCashOutRequest(
            amount = amount.toDouble(),
            pixKey = pixKey,
            pixKeyType = pixKeyType,
            description = description,
            webhookUrl = properties.webhookUrl
        )

        log.info("Initiating cashout - amount: {}, pixKey: {}..., type: {}", amount, pixKey.take(6), pixKeyType)

        val response = restClient.post()
            .uri("/api/partner/v1/cashout")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .body(request)
            .retrieve()
            .body(SyncPayCashOutResponse::class.java)
            ?: throw RuntimeException("Empty response from SyncPay cash-out")

        log.info("Cashout initiated - identifier: {}, status: {}", response.identifier, response.status)
        return response
    }

    fun getTransactionStatus(identifier: String): SyncPayTransactionResponse {
        val token = getValidToken()

        return restClient.get()
            .uri("/api/partner/v1/transaction/{identifier}", identifier)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .retrieve()
            .body(SyncPayTransactionResponse::class.java)
            ?: throw RuntimeException("Empty response from SyncPay transaction status")
    }

    private fun getValidToken(): String {
        val current = accessToken
        if (current != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
            return current
        }
        return refreshToken()
    }

    @Synchronized
    private fun refreshToken(): String {
        // Double-check after acquiring lock
        val current = accessToken
        if (current != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
            return current
        }

        log.info("Refreshing SyncPay auth token")

        val request = SyncPayAuthRequest(
            clientId = properties.clientId,
            clientSecret = properties.clientSecret
        )

        val response = restClient.post()
            .uri("/api/partner/v1/auth-token")
            .header("Accept", "application/json")
            .body(request)
            .retrieve()
            .body(SyncPayAuthResponse::class.java)
            ?: throw RuntimeException("Empty response from SyncPay auth")

        accessToken = response.accessToken
        tokenExpiresAt = Instant.now().plusSeconds((response.expiresIn ?: 3600).toLong())

        log.info("SyncPay token refreshed, expires at {}", tokenExpiresAt)
        return response.accessToken
    }
}
