package com.bojogar.bot.util

import kotlin.random.Random

object CodeGenerator {

    private const val CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6

    fun generatePeladaCode(): String {
        return (1..CODE_LENGTH)
            .map { CHARS[Random.nextInt(CHARS.length)] }
            .joinToString("")
    }
}
