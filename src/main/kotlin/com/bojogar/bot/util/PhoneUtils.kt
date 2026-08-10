package com.bojogar.bot.util

object PhoneUtils {

    fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")

        return when {
            // Already full international: 55 + DDD(2) + 9 + number(8) = 13 digits
            digits.length == 13 && digits.startsWith("55") -> digits
            // International without 9: 55 + DDD(2) + number(8) = 12 digits
            digits.length == 12 && digits.startsWith("55") -> "${digits.substring(0, 4)}9${digits.substring(4)}"
            // Local with 9: DDD(2) + 9 + number(8) = 11 digits
            digits.length == 11 -> "55$digits"
            // Local without 9: DDD(2) + number(8) = 10 digits
            digits.length == 10 -> "55${digits.substring(0, 2)}9${digits.substring(2)}"
            // Return as-is if format is unrecognized
            else -> digits
        }
    }

    fun formatPhoneDisplay(phone: String): String {
        val digits = normalizePhone(phone)
        if (digits.length != 13) return phone

        val ddd = digits.substring(2, 4)
        val part1 = digits.substring(4, 9)
        val part2 = digits.substring(9)
        return "+55 ($ddd) $part1-$part2"
    }
}
