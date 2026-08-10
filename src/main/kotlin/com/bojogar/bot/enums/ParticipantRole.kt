package com.bojogar.bot.enums

enum class ParticipantRole {
    OWNER,
    ADMIN,
    PLAYER;

    fun hasAuthority(required: ParticipantRole): Boolean = this.ordinal <= required.ordinal
}
