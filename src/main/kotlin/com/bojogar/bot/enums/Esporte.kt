package com.bojogar.bot.enums

enum class Esporte(val label: String, val emoji: String) {
    FUTEBOL("Futebol", "\u26BD"),
    FUTEVOLEI("Futevôlei", "\uD83C\uDFD6\uFE0F"),
    VOLEI("Vôlei", "\uD83C\uDFD0");

    val display: String get() = "$emoji $label"
}
