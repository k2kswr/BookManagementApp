package com.example.bookmanagement.common

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

fun notFound(resource: String, id: Long): Nothing = throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "$resource $id was not found")

fun invalidInput(message: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message)
