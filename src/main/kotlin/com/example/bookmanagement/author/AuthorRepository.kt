package com.example.bookmanagement.author

import com.example.bookmanagement.jooq.tables.Authors.AUTHORS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class AuthorRepository(private val dsl: DSLContext) {
    fun create(request: AuthorRequest): AuthorResponse {
        val record = checkNotNull(
            dsl.insertInto(AUTHORS)
                .set(AUTHORS.NAME, request.name)
                .set(AUTHORS.BIRTH_DATE, request.birthDate)
                .returning()
                .fetchOne(),
        )
        return AuthorResponse(checkNotNull(record.id), checkNotNull(record.name), checkNotNull(record.birthDate))
    }

    fun update(id: Long, request: AuthorRequest): AuthorResponse? = dsl.update(AUTHORS)
        .set(AUTHORS.NAME, request.name)
        .set(AUTHORS.BIRTH_DATE, request.birthDate)
        .where(AUTHORS.ID.eq(id))
        .returning()
        .fetchOne()
        ?.let { AuthorResponse(checkNotNull(it.id), checkNotNull(it.name), checkNotNull(it.birthDate)) }

    fun exists(id: Long): Boolean = dsl.fetchExists(AUTHORS, AUTHORS.ID.eq(id))

    fun existingIds(ids: List<Long>): Set<Long> = dsl.select(AUTHORS.ID).from(AUTHORS).where(AUTHORS.ID.`in`(ids))
        .fetch(AUTHORS.ID).map { checkNotNull(it) }.toSet()
}
