package com.bojogar.bot.exception

open class ApiException(
    val status: Int,
    override val message: String
) : RuntimeException(message)
