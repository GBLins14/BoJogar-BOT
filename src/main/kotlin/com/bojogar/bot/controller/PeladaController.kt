package com.bojogar.bot.controller

import com.bojogar.bot.dto.request.CreatePeladaRequest
import com.bojogar.bot.dto.request.UpdatePeladaRequest
import com.bojogar.bot.dto.response.PeladaResponse
import com.bojogar.bot.exception.ResourceNotFoundException
import com.bojogar.bot.service.PeladaService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/api/peladas")
class PeladaController(
    private val peladaService: PeladaService
) {

    @PostMapping
    fun create(
        @RequestHeader("X-User-Phone") phone: String,
        @Valid @RequestBody request: CreatePeladaRequest
    ): ResponseEntity<PeladaResponse> {
        val pelada = peladaService.create(phone, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(pelada)
    }

    @GetMapping("/{code}")
    fun findByCode(@PathVariable code: String): PeladaResponse {
        return peladaService.findByCode(code)
            ?: throw ResourceNotFoundException("Pelada nao encontrada: $code")
    }

    @GetMapping("/user")
    fun findByUser(@RequestHeader("X-User-Phone") phone: String): List<PeladaResponse> {
        return peladaService.findByUser(phone)
    }

    @GetMapping("/created")
    fun findCreatedByUser(@RequestHeader("X-User-Phone") phone: String): List<PeladaResponse> {
        return peladaService.findCreatedByUser(phone)
    }

    @PatchMapping("/{code}")
    fun update(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") phone: String,
        @Valid @RequestBody request: UpdatePeladaRequest
    ): PeladaResponse {
        return peladaService.update(code, phone, request)
    }

    @PostMapping("/{code}/cancel")
    fun cancel(
        @PathVariable code: String,
        @RequestHeader("X-User-Phone") phone: String
    ): PeladaResponse {
        return peladaService.cancel(code, phone)
    }
}
