package com.bojogar.bot.service

import com.bojogar.bot.config.AbacatePayProperties
import com.bojogar.bot.dto.abacatepay.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class AbacatePayClient(
    @Qualifier("abacatePayRestClient") private val restClient: RestClient,
    private val properties: AbacatePayProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(AbacatePayClient::class.java)
    }

    fun generatePix(
        amount: BigDecimal,
        description: String?,
        expiresInSeconds: Int,
        customer: AbacatePayCustomer
    ): AbacatePayTransparentResponse {
        val amountCents = amount.multiply(BigDecimal(100)).intValueExact()

        val request = AbacatePayTransparentRequest(
            method = "PIX",
            data = AbacatePayTransparentData(
                amount = amountCents,
                description = description,
                expiresIn = expiresInSeconds,
                customer = customer
            )
        )

        log.info("Generating PIX via AbacatePay - amount: {} ({}c), customer: {}", amount, amountCents, customer.taxId)

        val envelope = restClient.post()
            .uri("/transparents/create")
            .body(request)
            .retrieve()
            .body(object : ParameterizedTypeReference<AbacatePayEnvelope<AbacatePayTransparentResponse>>() {})
            ?: throw RuntimeException("Empty response from AbacatePay")

        if (envelope.success != true || envelope.data == null) {
            throw RuntimeException("AbacatePay error: ${envelope.error}")
        }

        log.info("PIX generated via AbacatePay - id: {}", envelope.data.id)
        return envelope.data
    }

    fun checkPaymentStatus(transparentId: String): AbacatePayTransparentResponse {
        val envelope = restClient.get()
            .uri("/transparents/check?id=$transparentId")
            .retrieve()
            .body(object : ParameterizedTypeReference<AbacatePayEnvelope<AbacatePayTransparentResponse>>() {})
            ?: throw RuntimeException("Empty response from AbacatePay")

        if (envelope.success != true || envelope.data == null) {
            throw RuntimeException("AbacatePay error: ${envelope.error}")
        }

        return envelope.data
    }
}
