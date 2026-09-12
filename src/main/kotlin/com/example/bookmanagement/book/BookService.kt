package com.example.bookmanagement.book

import com.example.bookmanagement.author.AuthorRepository
import com.example.bookmanagement.common.invalidInput
import com.example.bookmanagement.common.notFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(private val repository: BookRepository, private val authors: AuthorRepository) {
    @Transactional
    fun create(request: BookRequest): BookResponse {
        validateAuthors(request.authorIds)
        val id = repository.create(request)
        repository.replaceAuthors(id, request.authorIds)
        return repository.find(id)
    }

    @Transactional
    fun update(id: Long, request: BookRequest): BookResponse {
        val currentStatus = repository.lockStatus(id) ?: notFound("Book", id)
        currentStatus.requireTransitionTo(request.publicationStatus)
        validateAuthors(request.authorIds)
        repository.update(id, request)
        repository.replaceAuthors(id, request.authorIds)
        return repository.find(id)
    }

    @Transactional(readOnly = true)
    fun findByAuthor(authorId: Long): List<BookResponse> {
        if (!authors.exists(authorId)) notFound("Author", authorId)
        return repository.findByAuthor(authorId)
    }

    private fun validateAuthors(ids: List<Long>) {
        if (ids.isEmpty()) invalidInput("At least one author is required")
        if (ids.distinct().size != ids.size) invalidInput("authorIds must not contain duplicates")
        val missingIds = ids.toSet() - authors.existingIds(ids)
        if (missingIds.isNotEmpty()) invalidInput("Unknown authorIds: ${missingIds.sorted().joinToString()}")
    }
}
