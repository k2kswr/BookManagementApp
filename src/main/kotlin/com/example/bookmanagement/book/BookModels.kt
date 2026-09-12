package com.example.bookmanagement.book

import com.example.bookmanagement.author.AuthorSummary
import com.example.bookmanagement.common.ApiException
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import java.math.BigDecimal

enum class PublicationStatus {
    UNPUBLISHED,
    PUBLISHED,
    ;

    fun requireTransitionTo(next: PublicationStatus) {
        if (this == PUBLISHED && next == UNPUBLISHED) {
            throw ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Published books cannot become unpublished")
        }
    }
}

data class BookRequest(
    @field:NotBlank val title: String,
    @field:DecimalMin("0") @field:Digits(integer = 10, fraction = 2) val price: BigDecimal,
    @field:NotEmpty val authorIds: List<Long>,
    val publicationStatus: PublicationStatus,
)

data class BookResponse(
    val id: Long,
    val title: String,
    val price: BigDecimal,
    val publicationStatus: PublicationStatus,
    val authors: List<AuthorSummary>,
)
