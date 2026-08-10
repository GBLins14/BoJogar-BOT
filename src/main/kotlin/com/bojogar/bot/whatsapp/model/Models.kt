package com.bojogar.bot.whatsapp.model

data class Button(
    val id: String,
    val title: String
)

data class ListSection(
    val title: String,
    val rows: List<ListRow>
)

data class ListRow(
    val id: String,
    val title: String,
    val description: String? = null
)
