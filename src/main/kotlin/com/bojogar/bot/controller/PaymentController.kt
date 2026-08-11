package com.bojogar.bot.controller

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.service.PagamentoService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/api/peladas/{code}/payments")
class PaymentController(
    private val pagamentoService: PagamentoService
) {

    @GetMapping
    fun getPayments(@PathVariable code: String): List<PaymentResponse> {
        return pagamentoService.getPaymentsByPelada(code)
    }

    @GetMapping("/unpaid")
    fun getUnpaid(@PathVariable code: String): List<ParticipantResponse> {
        return pagamentoService.getUnpaidParticipants(code)
    }

    @PostMapping("/{participantId}/confirm")
    fun confirmPayment(
        @PathVariable code: String,
        @PathVariable participantId: UUID,
        @RequestHeader("X-User-Phone") requesterPhone: String
    ): PaymentResponse {
        return pagamentoService.confirmPayment(participantId, requesterPhone)
    }
}
