package com.bojogar.bot.whatsapp.service

import com.bojogar.bot.whatsapp.client.WhatsAppClient
import com.bojogar.bot.whatsapp.model.*
import org.springframework.stereotype.Service

@Service
class WhatsAppServiceImpl(
    private val client: WhatsAppClient
) : WhatsAppService {

    override fun sendMessage(to: String, text: String) {
        client.sendPayload(
            MessagePayload(
                to = to,
                type = "text",
                text = TextBody(body = text)
            )
        )
    }

    override fun sendButtons(
        to: String,
        body: String,
        buttons: List<Button>,
        header: String?,
        footer: String?
    ) {
        client.sendPayload(
            MessagePayload(
                to = to,
                type = "interactive",
                interactive = InteractiveBody(
                    type = "button",
                    header = header?.let { InteractiveHeader(text = it) },
                    body = InteractiveTextBody(text = body),
                    footer = footer?.let { InteractiveTextBody(text = it) },
                    action = InteractiveAction(
                        buttons = buttons.map { btn ->
                            InteractiveButton(reply = InteractiveReply(id = btn.id, title = btn.title))
                        }
                    )
                )
            )
        )
    }

    override fun sendList(
        to: String,
        body: String,
        buttonLabel: String,
        sections: List<ListSection>,
        header: String?,
        footer: String?
    ) {
        client.sendPayload(
            MessagePayload(
                to = to,
                type = "interactive",
                interactive = InteractiveBody(
                    type = "list",
                    header = header?.let { InteractiveHeader(text = it) },
                    body = InteractiveTextBody(text = body),
                    footer = footer?.let { InteractiveTextBody(text = it) },
                    action = InteractiveAction(
                        button = buttonLabel,
                        sections = sections.map { section ->
                            InteractiveSectionBody(
                                title = section.title,
                                rows = section.rows.map { row ->
                                    InteractiveRowBody(
                                        id = row.id,
                                        title = row.title,
                                        description = row.description
                                    )
                                }
                            )
                        }
                    )
                )
            )
        )
    }

    override fun markAsRead(messageId: String) {
        client.markAsRead(messageId)
    }
}
