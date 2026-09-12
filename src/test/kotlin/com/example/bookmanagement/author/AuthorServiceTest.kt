package com.example.bookmanagement.author

import com.example.bookmanagement.common.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AuthorServiceTest {
    private val repository = mock(AuthorRepository::class.java)

    // UTC is still September 11; the business date in Tokyo is September 12.
    private val clock = Clock.fixed(Instant.parse("2026-09-11T15:00:00Z"), ZoneId.of("Asia/Tokyo"))
    private val service = AuthorService(repository, clock)

    @ParameterizedTest
    @ValueSource(strings = ["2026-09-11", "2026-09-12"])
    fun `accepts yesterday and today in Tokyo`(date: String) {
        val request = AuthorRequest("Author", LocalDate.parse(date))
        val response = AuthorResponse(1, request.name, request.birthDate)
        `when`(repository.create(request)).thenReturn(response)
        `when`(repository.update(1, request)).thenReturn(response)
        assertThat(service.create(request)).isEqualTo(response)
        assertThat(service.update(1, request)).isEqualTo(response)
    }

    @Test
    fun `rejects tomorrow before writing on both create and update`() {
        val request = AuthorRequest("Author", LocalDate.parse("2026-09-13"))
        assertThatThrownBy { service.create(request) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { service.update(1, request) }.isInstanceOf(ApiException::class.java)
        verifyNoInteractions(repository)
    }
}
