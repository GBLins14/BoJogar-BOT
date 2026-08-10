package com.bojogar.bot.whatsapp.service

import com.bojogar.bot.whatsapp.model.Button
import com.bojogar.bot.whatsapp.model.ListSection

interface WhatsAppService {

    fun sendMessage(to: String, text: String)

    fun sendButtons(
        to: String,
        body: String,
        buttons: List<Button>,
        header: String? = null,
        footer: String? = null
    )

    fun sendList(
        to: String,
        body: String,
        buttonLabel: String,
        sections: List<ListSection>,
        header: String? = null,
        footer: String? = null
    )

    fun markAsRead(messageId: String)
}
