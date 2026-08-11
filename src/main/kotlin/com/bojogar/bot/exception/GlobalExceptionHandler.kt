package com.bojogar.bot.exception

import com.bojogar.bot.dto.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {
        log.warn("ApiException [{}]: {}", ex.status, ex.message)
        val error = ErrorResponse(
            status = ex.status,
            error = HttpStatus.valueOf(ex.status).reasonPhrase,
            message = ex.message
        )
        return ResponseEntity.status(ex.status).body(error)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", message)
        val error = ErrorResponse(
            status = 400,
            error = "Bad Request",
            message = message
        )
        return ResponseEntity.badRequest().body(error)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.error(">>> ERRO deserializacao JSON: {}", ex.message, ex)
        val error = ErrorResponse(
            status = 400,
            error = "Bad Request",
            message = "JSON invalido ou mal formatado"
        )
        return ResponseEntity.badRequest().body(error)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error(">>> ERRO inesperado: {}", ex.message, ex)
        val error = ErrorResponse(
            status = 500,
            error = "Internal Server Error",
            message = "Erro interno do servidor"
        )
        return ResponseEntity.status(500).body(error)
    }
}
