package com.example.bookmanagement.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(exception: ApiException): ProblemDetail = problem(exception.status, exception.code, exception.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Request validation failed").apply {
        setProperty(
            "errors",
            exception.bindingResult.fieldErrors.map {
                mapOf("field" to it.field, "message" to (it.defaultMessage ?: "Invalid value"))
            },
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun handleMalformedRequest(exception: Exception): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Malformed JSON, missing required field, or invalid value")

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(exception: ResponseStatusException): ProblemDetail = ProblemDetail.forStatusAndDetail(exception.statusCode, exception.reason ?: "Request failed").apply {
        setProperty("code", "HTTP_ERROR")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ProblemDetail {
        if (exception is ErrorResponse) {
            return exception.body.apply { setProperty("code", "HTTP_ERROR") }
        }
        logger.error("Unexpected request failure", exception)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun problem(status: HttpStatus, code: String, detail: String): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}
