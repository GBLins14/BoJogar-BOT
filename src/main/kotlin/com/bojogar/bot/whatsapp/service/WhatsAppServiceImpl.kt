package com.bojogar.bot.whatsapp.service

import com.bojogar.bot.whatsapp.client.WhatsAppClient
import com.bojogar.bot.whatsapp.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WhatsAppServiceImpl(
    private val client: WhatsAppClient
) : WhatsAppService {

    companion object {
        private val log = LoggerFactory.getLogger(WhatsAppServiceImpl::class.java)
    }

    override fun sendMessage(to: String, text: String) {
        log.info("Enviando mensagem de texto para {}: \"{}\"", to, text.take(80).replace("\n", " "))
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
        log.info("Enviando botões para {}: [{}]", to, buttons.joinToString(", ") { it.title })
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
        val totalRows = sections.sumOf { it.rows.size }
        log.info("Enviando lista para {}: {} seções, {} itens", to, sections.size, totalRows)
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
        log.debug("Marcando mensagem {} como lida", messageId)
        client.markAsRead(messageId)
    }
}
