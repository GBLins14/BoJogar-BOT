package com.bojogar.bot.enums

enum class StatusPelada {
    DRAFT,
    OPEN,
    FULL,
    IN_PROGRESS,
    FINISHED,
    CANCELLED;

    fun canTransitionTo(target: StatusPelada): Boolean = when (this) {
        DRAFT -> target in listOf(OPEN, CANCELLED)
        OPEN -> target in listOf(FULL, IN_PROGRESS, CANCELLED)
        FULL -> target in listOf(OPEN, IN_PROGRESS, CANCELLED)
        IN_PROGRESS -> target == FINISHED
        FINISHED, CANCELLED -> false
    }
}
