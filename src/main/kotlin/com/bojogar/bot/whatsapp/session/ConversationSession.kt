package com.bojogar.bot.whatsapp.session

import java.time.Instant

data class ConversationSession(
    val state: ConversationState,
    val currentPeladaCode: String? = null,
    val collectedFields: Map<String, String> = emptyMap(),
    val nextField: String? = null,
    val createdAt: Instant = Instant.now()
) {
    companion object {
        private const val TTL_SECONDS = 1800L // 30 minutes
    }

    fun isExpired(): Boolean = Instant.now().epochSecond - createdAt.epochSecond > TTL_SECONDS

    fun withField(key: String, value: String, next: String?): ConversationSession {
        return copy(
            collectedFields = collectedFields + (key to value),
            nextField = next,
            createdAt = Instant.now()
        )
    }
}
