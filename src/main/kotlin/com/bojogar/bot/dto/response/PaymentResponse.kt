package com.bojogar.bot.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentResponse(
    val id: UUID,
    val participantId: UUID,
    val participantName: String,
    val participantPhone: String,
    val valor: BigDecimal,
    val status: String,
    val transactionId: String?,
    val paidAt: Instant?
)
