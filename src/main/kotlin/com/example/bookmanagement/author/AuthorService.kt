package com.example.bookmanagement.author

import com.example.bookmanagement.common.invalidInput
import com.example.bookmanagement.common.notFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class AuthorService(private val repository: AuthorRepository, private val clock: Clock) {
    @Transactional
    fun create(request: AuthorRequest): AuthorResponse {
        validateBirthDate(request.birthDate)
        return repository.create(request)
    }

    @Transactional
    fun update(id: Long, request: AuthorRequest): AuthorResponse {
        validateBirthDate(request.birthDate)
        return repository.update(id, request) ?: notFound("Author", id)
    }

    private fun validateBirthDate(birthDate: LocalDate) {
        if (birthDate.isAfter(LocalDate.now(clock))) {
            invalidInput("birthDate must be today or earlier (Asia/Tokyo)")
        }
    }
}
