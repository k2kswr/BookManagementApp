package com.example.bookmanagement.author

import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class AuthorRequest(
    @field:NotBlank val name: String,
    val birthDate: LocalDate,
)

data class AuthorResponse(val id: Long, val name: String, val birthDate: LocalDate)

data class AuthorSummary(val id: Long, val name: String)
