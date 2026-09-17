package com.example.bookmanagement.book

import com.example.bookmanagement.author.AuthorSummary
import com.example.bookmanagement.jooq.tables.Authors.AUTHORS
import com.example.bookmanagement.jooq.tables.BookAuthors.BOOK_AUTHORS
import com.example.bookmanagement.jooq.tables.Books.BOOKS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class BookRepository(private val dsl: DSLContext) {
    fun findAll(): List<BookResponse> {
        val ids = dsl.select(BOOKS.ID).from(BOOKS).orderBy(BOOKS.ID).fetch(BOOKS.ID).map { checkNotNull(it) }
        return findAll(ids)
    }

    fun create(request: BookRequest): Long = checkNotNull(
        dsl.insertInto(BOOKS)
            .set(BOOKS.TITLE, request.title)
            .set(BOOKS.PRICE, request.price)
            .set(BOOKS.PUBLICATION_STATUS, request.publicationStatus.name)
            .returning(BOOKS.ID)
            .fetchOne()?.id,
    )

    fun lockStatus(id: Long): PublicationStatus? = dsl.select(BOOKS.PUBLICATION_STATUS).from(BOOKS).where(BOOKS.ID.eq(id))
        .forUpdate().fetchOne(BOOKS.PUBLICATION_STATUS)?.let(PublicationStatus::valueOf)

    fun update(id: Long, request: BookRequest) {
        dsl.update(BOOKS)
            .set(BOOKS.TITLE, request.title)
            .set(BOOKS.PRICE, request.price)
            .set(BOOKS.PUBLICATION_STATUS, request.publicationStatus.name)
            .where(BOOKS.ID.eq(id)).execute()
    }

    fun replaceAuthors(id: Long, authorIds: List<Long>) {
        dsl.deleteFrom(BOOK_AUTHORS).where(BOOK_AUTHORS.BOOK_ID.eq(id)).execute()
        dsl.batch(
            authorIds.map { authorId ->
                dsl.insertInto(BOOK_AUTHORS)
                    .set(BOOK_AUTHORS.BOOK_ID, id)
                    .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
            },
        ).execute()
    }

    fun find(id: Long): BookResponse = findAll(listOf(id)).single()

    fun findByAuthor(authorId: Long): List<BookResponse> {
        val ids = dsl.select(BOOK_AUTHORS.BOOK_ID).from(BOOK_AUTHORS)
            .where(BOOK_AUTHORS.AUTHOR_ID.eq(authorId))
            .fetch(BOOK_AUTHORS.BOOK_ID).map { checkNotNull(it) }
        return findAll(ids)
    }

    private fun findAll(ids: List<Long>): List<BookResponse> {
        if (ids.isEmpty()) return emptyList()
        val rows = dsl.select(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOKS.PRICE,
            BOOKS.PUBLICATION_STATUS,
            AUTHORS.ID,
            AUTHORS.NAME,
        ).from(BOOKS)
            .join(BOOK_AUTHORS).on(BOOK_AUTHORS.BOOK_ID.eq(BOOKS.ID))
            .join(AUTHORS).on(AUTHORS.ID.eq(BOOK_AUTHORS.AUTHOR_ID))
            .where(BOOKS.ID.`in`(ids))
            .orderBy(BOOKS.ID, AUTHORS.ID)
            .fetch()
        return rows.groupBy { checkNotNull(it[BOOKS.ID]) }.map { (id, authors) ->
            val first = authors.first()
            BookResponse(
                id,
                checkNotNull(first[BOOKS.TITLE]),
                checkNotNull(first[BOOKS.PRICE]),
                PublicationStatus.valueOf(checkNotNull(first[BOOKS.PUBLICATION_STATUS])),
                authors.map { AuthorSummary(checkNotNull(it[AUTHORS.ID]), checkNotNull(it[AUTHORS.NAME])) },
            )
        }
    }
}
