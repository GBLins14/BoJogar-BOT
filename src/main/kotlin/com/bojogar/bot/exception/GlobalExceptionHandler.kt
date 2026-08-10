package com.bojogar.bot.exception

import com.bojogar.bot.dto.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {
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
        val error = ErrorResponse(
            status = 400,
            error = "Bad Request",
            message = message
        )
        return ResponseEntity.badRequest().body(error)
    }
}
