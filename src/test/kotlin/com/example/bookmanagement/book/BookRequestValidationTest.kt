package com.example.bookmanagement.book

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class BookRequestValidationTest {
    @ParameterizedTest
    @CsvSource("0,true", "0.01,true", "9999999999.99,true", "-0.01,false", "10000000000,false", "0.001,false")
    fun `validates exact decimal boundaries`(price: BigDecimal, valid: Boolean) {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val request = BookRequest("Kotlin", price, listOf(1), PublicationStatus.UNPUBLISHED)
            assertThat(factory.validator.validate(request).isEmpty()).isEqualTo(valid)
        }
    }
}
