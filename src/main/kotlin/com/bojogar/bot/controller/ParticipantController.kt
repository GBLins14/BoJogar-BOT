package com.bojogar.bot.controller

import com.bojogar.bot.dto.response.ParticipantResponse
import com.bojogar.bot.service.JoinResult
import com.bojogar.bot.service.LeaveResult
import com.bojogar.bot.service.ParticipantService
import com.bojogar.bot.service.RemoveResult
import com.bojogar.bot.exception.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/api/peladas/{code}/participants")
class ParticipantController(
    private val participantService: ParticipantService
) {

    @GetMapping
    fun getParticipants(@PathVariable code: String): List<ParticipantResponse> {
        return participantService.getParticipants(code)
    }

    @PostMapping("/join")
    fun join(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") phone: String
    ): ResponseEntity<Any> {
        return when (val result = participantService.join(phone, code)) {
            is JoinResult.Confirmed -> ResponseEntity.status(HttpStatus.CREATED).body(result.participant)
            is JoinResult.PendingPayment -> ResponseEntity.status(HttpStatus.CREATED).body(mapOf("status" to "PENDING_PAYMENT", "participant" to result.participant))
            is JoinResult.Waitlisted -> ResponseEntity.ok(mapOf("status" to "WAITLIST", "position" to result.position))
            is JoinResult.AlreadyJoined -> throw BusinessException("Ja inscrito nesta pelada")
            is JoinResult.PeladaClosed -> throw BusinessException("Pelada nao esta aberta")
            is JoinResult.Error -> throw BusinessException(result.message)
        }
    }

    @PostMapping("/leave")
    fun leave(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") phone: String
    ): ResponseEntity<Any> {
        return when (val result = participantService.leave(phone, code)) {
            is LeaveResult.Left -> ResponseEntity.ok(mapOf("status" to "LEFT", "promoted" to result.promoted))
            is LeaveResult.NotFound -> throw BusinessException("Inscricao nao encontrada")
            is LeaveResult.Error -> throw BusinessException(result.message)
        }
    }

    @DeleteMapping("/{targetPhone}")
    fun remove(
        @PathVariable code: String,
        @PathVariable targetPhone: String,
        @RequestHeader("X-User-Phone") requesterPhone: String
    ): ResponseEntity<Any> {
        return when (val result = participantService.removeParticipant(requesterPhone, targetPhone, code)) {
            is RemoveResult.Removed -> ResponseEntity.ok(mapOf("status" to "REMOVED", "promoted" to result.promoted))
            is RemoveResult.NotFound -> throw BusinessException("Participante nao encontrado")
            is RemoveResult.Unauthorized -> throw BusinessException("Sem permissao")
            is RemoveResult.Error -> throw BusinessException(result.message)
        }
    }
}
