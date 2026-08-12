package com.bojogar.bot.controller

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.dto.response.PaymentResponse
import com.bojogar.bot.service.AuthorizationService
import com.bojogar.bot.service.PagamentoService
import com.bojogar.bot.exception.BusinessException
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/api/peladas/{code}/payments")
class PaymentController(
    private val pagamentoService: PagamentoService,
    private val authorizationService: AuthorizationService
) {

    @GetMapping
    fun getPayments(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") requesterPhone: String
    ): List<PaymentResponse> {
        if (!authorizationService.isAdminOrOwner(requesterPhone, code)) {
            throw BusinessException("Sem permissão para ver pagamentos desta pelada")
        }
        return pagamentoService.getPaymentsByPelada(code)
    }

    @GetMapping("/unpaid")
    fun getUnpaid(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") requesterPhone: String
    ): List<ParticipantResponse> {
        if (!authorizationService.isAdminOrOwner(requesterPhone, code)) {
            throw BusinessException("Sem permissão para ver pagamentos desta pelada")
        }
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
