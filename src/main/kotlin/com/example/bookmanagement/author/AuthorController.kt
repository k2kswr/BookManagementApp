package com.example.bookmanagement.author

import com.example.bookmanagement.book.BookResponse
import com.example.bookmanagement.book.BookService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/authors")
class AuthorController(private val service: AuthorService, private val bookService: BookService) {
    @GetMapping
    fun findAll(): List<AuthorResponse> = service.findAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: AuthorRequest): AuthorResponse = service.create(request)

    @PutMapping("/{authorId}")
    fun update(@PathVariable authorId: Long, @Valid @RequestBody request: AuthorRequest): AuthorResponse = service.update(authorId, request)

    @GetMapping("/{authorId}/books")
    fun books(@PathVariable authorId: Long): List<BookResponse> = bookService.findByAuthor(authorId)
}
