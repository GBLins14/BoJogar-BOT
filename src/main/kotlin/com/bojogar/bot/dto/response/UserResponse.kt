package com.bojogar.bot.dto.response

import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val phone: String,
    val name: String,
    val createdAt: Instant?
)
