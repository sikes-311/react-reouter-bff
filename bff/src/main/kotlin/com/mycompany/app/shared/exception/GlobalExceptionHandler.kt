package com.mycompany.app.shared.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(error = "Unauthorized", status = 401, detail = e.message ?: ""))

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(e: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "Not Found", status = 404, detail = e.message ?: ""))

    @ExceptionHandler(DownstreamClientException::class)
    fun handleDownstreamClient(e: DownstreamClientException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(error = "Bad Gateway", status = 502, detail = e.message ?: ""))

    @ExceptionHandler(DownstreamServerException::class)
    fun handleDownstreamServer(e: DownstreamServerException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(error = "Bad Gateway", status = 502, detail = e.message ?: ""))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "Bad Request", status = 400, detail = detail))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "Internal Server Error", status = 500, detail = "Unexpected error occurred"))
}
