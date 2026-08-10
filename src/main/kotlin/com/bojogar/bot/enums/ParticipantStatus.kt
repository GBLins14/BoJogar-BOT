package com.bojogar.bot.enums

enum class ParticipantStatus {
    CONFIRMED,
    WAITLIST,
    CANCELLED,
    REMOVED;

    fun canTransitionTo(target: ParticipantStatus): Boolean = when (this) {
        CONFIRMED -> target in listOf(CANCELLED, REMOVED)
        WAITLIST -> target in listOf(CONFIRMED, CANCELLED, REMOVED)
        CANCELLED, REMOVED -> false
    }
}
