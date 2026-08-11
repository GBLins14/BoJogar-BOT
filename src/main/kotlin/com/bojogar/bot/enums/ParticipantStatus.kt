package com.bojogar.bot.enums

enum class ParticipantStatus {
    CONFIRMED,
    PENDING_PAYMENT,
    WAITLIST,
    CANCELLED,
    REMOVED;

    fun canTransitionTo(target: ParticipantStatus): Boolean = when (this) {
        CONFIRMED -> target in listOf(CANCELLED, REMOVED)
        PENDING_PAYMENT -> target in listOf(CONFIRMED, CANCELLED, REMOVED)
        WAITLIST -> target in listOf(CONFIRMED, PENDING_PAYMENT, CANCELLED, REMOVED)
        CANCELLED, REMOVED -> false
    }
}
