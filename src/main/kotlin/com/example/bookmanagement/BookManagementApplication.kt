package com.example.bookmanagement

import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock
import java.time.ZoneId

@SpringBootApplication
class BookManagementApplication {
    @Bean
    fun kotlinModule(): KotlinModule = KotlinModule.Builder().enable(KotlinFeature.StrictNullChecks).build()

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Tokyo"))
}

fun main(args: Array<String>) {
    runApplication<BookManagementApplication>(*args)
}
