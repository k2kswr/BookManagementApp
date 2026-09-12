package com.example.bookmanagement.book

import com.example.bookmanagement.common.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus

class PublicationStatusTest {
    @ParameterizedTest
    @CsvSource("UNPUBLISHED, UNPUBLISHED", "UNPUBLISHED, PUBLISHED", "PUBLISHED, PUBLISHED")
    fun `permits valid status transitions`(current: PublicationStatus, next: PublicationStatus) {
        assertThatCode { current.requireTransitionTo(next) }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @CsvSource("PUBLISHED, UNPUBLISHED")
    fun `rejects returning to unpublished`(current: PublicationStatus, next: PublicationStatus) {
        val exception = catchThrowableOfType(ApiException::class.java) { current.requireTransitionTo(next) }
        assertThat(exception.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(exception.code).isEqualTo("INVALID_STATUS_TRANSITION")
    }
}
