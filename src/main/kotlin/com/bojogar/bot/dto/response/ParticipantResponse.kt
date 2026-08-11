package com.bojogar.bot.dto.response

import java.time.Instant
import java.util.UUID

data class ParticipantResponse(
    val id: UUID,
    val userId: UUID,
    val userName: String,
    val userPhone: String,
    val peladaCodigo: String,
    val role: String,
    val displayName: String?,
    val shirtNumber: Int?,
    val status: String,
    val waitlistPosition: Int?,
    val joinedAt: Instant?
)
