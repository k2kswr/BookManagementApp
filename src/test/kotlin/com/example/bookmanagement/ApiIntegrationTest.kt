package com.example.bookmanagement

import com.example.bookmanagement.author.AuthorRequest
import com.example.bookmanagement.author.AuthorService
import com.example.bookmanagement.book.BookRequest
import com.example.bookmanagement.book.BookService
import com.example.bookmanagement.book.PublicationStatus
import com.example.bookmanagement.common.ApiException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Import(ApiIntegrationTest.FixedClockConfiguration::class)
class ApiIntegrationTest {
    companion object {
        private val postgres = lazy { PostgreSQLContainer("postgres:17.6").apply { start() } }

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            val externalUrl = System.getenv("TEST_DB_URL")
            registry.add("spring.datasource.url") { externalUrl ?: postgres.value.jdbcUrl }
            registry.add("spring.datasource.username") {
                if (externalUrl == null) postgres.value.username else System.getenv("TEST_DB_USER") ?: "books"
            }
            registry.add("spring.datasource.password") {
                if (externalUrl == null) postgres.value.password else System.getenv("TEST_DB_PASSWORD") ?: "books"
            }
        }

        @AfterAll
        @JvmStatic
        fun stopDatabase() {
            if (postgres.isInitialized()) postgres.value.stop()
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfiguration {
        @Bean
        @Primary
        fun testClock(): Clock = Clock.fixed(Instant.parse("2026-09-11T15:00:00Z"), ZoneId.of("Asia/Tokyo"))
    }

    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var authors: AuthorService

    @Autowired private lateinit var books: BookService

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun resetDatabase() {
        dsl.execute("TRUNCATE book_authors, books, authors RESTART IDENTITY CASCADE")
    }

    @Test
    fun `registers and updates an author with today's birth date`() {
        val created = mvc.perform(
            post("/authors").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Alice","birthDate":"2026-09-12"}"""),
        ).andExpect(status().isCreated).andReturn()
        val id = mapper.readTree(created.response.contentAsString)["id"].asLong()
        mvc.perform(
            put("/authors/$id").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Alice updated","birthDate":"2000-01-01"}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.name").value("Alice updated"))
        mvc.perform(get("/authors/$id/books")).andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `creates shared books and replaces all authors atomically`() {
        val alice = author("Alice")
        val bob = author("Bob")
        val carol = author("Carol")
        val shared = createBook(bookRequest(listOf(bob, alice)))
        val second = createBook(bookRequest(listOf(alice), title = "Second"))
        assertThat(shared["authors"].map { it["id"].asLong() }).containsExactly(alice, bob)
        val result = mvc.perform(get("/authors/$alice/books"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2)).andReturn()
        val fetched = mapper.readTree(result.response.contentAsString)
        assertThat(fetched.map { it["id"].asLong() }).containsExactly(shared["id"].asLong(), second["id"].asLong())
        assertThat(fetched[0]["authors"].map { it["id"].asLong() }).containsExactly(alice, bob)

        mvc.perform(
            put("/books/${shared["id"].asLong()}").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(bookRequest(listOf(carol), PublicationStatus.PUBLISHED, "Revised"))),
        ).andExpect(status().isOk).andExpect(jsonPath("$.title").value("Revised"))
            .andExpect(jsonPath("$.authors.length()").value(1)).andExpect(jsonPath("$.authors[0].id").value(carol))
        mvc.perform(get("/authors/$bob/books")).andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        mvc.perform(get("/authors/$alice/books")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `rejects unpublishing but allows editing published book details`() {
        val id = author("Author")
        val created = createBook(bookRequest(listOf(id), PublicationStatus.PUBLISHED))
        val bookId = created["id"].asLong()
        mvc.perform(
            put("/books/$bookId").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(bookRequest(listOf(id)))),
        ).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
        mvc.perform(
            put("/books/$bookId").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(bookRequest(listOf(id), PublicationStatus.PUBLISHED, "Edited"))),
        ).andExpect(status().isOk).andExpect(jsonPath("$.title").value("Edited"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["name", "birthDate"])
    fun `rejects absent and null author fields`(field: String) {
        val json = mapper.valueToTree<ObjectNode>(AuthorRequest("Author", LocalDate.parse("2000-01-01")))
        json.remove(field)
        assertBadAuthor(json.toString())
        json.putNull(field)
        assertBadAuthor(json.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["title", "price", "authorIds", "publicationStatus"])
    fun `rejects absent and null book fields`(field: String) {
        val json = mapper.valueToTree<ObjectNode>(bookRequest(listOf(author("Author"))))
        json.remove(field)
        assertBadBook(json.toString())
        json.putNull(field)
        assertBadBook(json.toString())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"name":" ","birthDate":"2000-01-01"}""",
            """{"name":"A","birthDate":"2026-09-13"}""",
            """{"name":"A","birthDate":"2025-02-30"}""",
            """{"name":"A","birthDate":"invalid"}""",
            """{"name":"A","birthDate":"2000-01-01","extra":1}""",
            """{"name":"A""",
        ],
    )
    fun `rejects invalid author JSON on create and update`(json: String) {
        assertBadAuthor(json)
    }

    @ParameterizedTest
    @ValueSource(strings = ["empty", "duplicate", "unknown", "nullElement", "negative", "precision", "scale", "title", "status"])
    fun `rejects invalid book values on create and update`(invalid: String) {
        val authorId = author("Author")
        val json = mapper.valueToTree<ObjectNode>(bookRequest(listOf(authorId)))
        when (invalid) {
            "empty" -> json.putArray("authorIds")
            "duplicate" -> json.putArray("authorIds").add(authorId).add(authorId)
            "unknown" -> json.putArray("authorIds").add(99999)
            "nullElement" -> json.putArray("authorIds").addNull()
            "negative" -> json.put("price", BigDecimal("-0.01"))
            "precision" -> json.put("price", BigDecimal("10000000000"))
            "scale" -> json.put("price", BigDecimal("0.001"))
            "title" -> json.put("title", " ")
            "status" -> json.put("publicationStatus", "UNKNOWN")
        }
        assertBadBook(json.toString())
    }

    @Test
    fun `returns 404 for missing resources`() {
        mvc.perform(get("/authors/999/books")).andExpect(status().isNotFound)
        mvc.perform(
            put("/authors/999").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"A","birthDate":"2000-01-01"}"""),
        ).andExpect(status().isNotFound)
        mvc.perform(
            put("/books/999").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(bookRequest(listOf(author("A"))))),
        ).andExpect(status().isNotFound)
        mvc.perform(get("/authors/not-a-number/books")).andExpect(status().isBadRequest)
    }

    @Test
    fun `database failure rolls back creation and replacement without leaking SQL`() {
        val originalAuthor = author("Original")
        val failingAuthor = author("Failing")
        val original = books.create(bookRequest(listOf(originalAuthor)))
        // Inject a real database failure after the book write and association deletion.
        dsl.execute(
            """
            CREATE FUNCTION test_reject_author() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                IF NEW.author_id = $failingAuthor THEN RAISE EXCEPTION 'test-only database detail'; END IF;
                RETURN NEW;
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        dsl.execute("CREATE TRIGGER test_reject_author BEFORE INSERT ON book_authors FOR EACH ROW EXECUTE FUNCTION test_reject_author()")
        try {
            val failingRequest = bookRequest(listOf(originalAuthor, failingAuthor), PublicationStatus.PUBLISHED, "Changed")
            val response = mvc.perform(
                put("/books/${original.id}").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(failingRequest)),
            ).andExpect(status().isInternalServerError).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn()
            assertThat(response.response.contentAsString).doesNotContain("test-only", "INSERT", "SQLException")
            assertThat(books.findByAuthor(originalAuthor)).containsExactly(original)
            assertThat(books.findByAuthor(failingAuthor)).isEmpty()
            assertThatThrownBy { books.create(failingRequest) }.isInstanceOf(RuntimeException::class.java)
            assertThat(dsl.fetchCount(dsl.selectFrom("books"))).isEqualTo(1)
            assertThat(books.findByAuthor(originalAuthor)).containsExactly(original)
        } finally {
            dsl.execute("DROP TRIGGER test_reject_author ON book_authors")
            dsl.execute("DROP FUNCTION test_reject_author()")
        }
    }

    @Test
    fun `concurrent update waits for publication and then rejects unpublishing`() {
        val authorId = author("Author")
        val original = books.create(bookRequest(listOf(authorId)))
        val published = CountDownLatch(1)
        val releasePublisher = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publisher = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    books.update(original.id, bookRequest(listOf(authorId), PublicationStatus.PUBLISHED))
                    published.countDown()
                    check(releasePublisher.await(15, TimeUnit.SECONDS))
                }
            }
            check(published.await(15, TimeUnit.SECONDS))
            val unpublisher = executor.submit { books.update(original.id, bookRequest(listOf(authorId))) }
            // Observe an actual PostgreSQL lock wait, not a timing-based assumption.
            await().atMost(Duration.ofSeconds(10)).until {
                dsl.fetchCount(
                    dsl.selectFrom("pg_stat_activity").where("datname = current_database() AND wait_event_type = 'Lock'"),
                ) > 0
            }
            releasePublisher.countDown()
            publisher.get(10, TimeUnit.SECONDS)
            assertThatThrownBy { unpublisher.get(10, TimeUnit.SECONDS) }
                .isInstanceOf(ExecutionException::class.java)
                .hasCauseInstanceOf(ApiException::class.java)
                .rootCause().hasMessage("Published books cannot become unpublished")
            assertThat(books.findByAuthor(authorId).single().publicationStatus).isEqualTo(PublicationStatus.PUBLISHED)
        } finally {
            releasePublisher.countDown()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private fun author(name: String): Long = authors.create(AuthorRequest(name, LocalDate.parse("2000-01-01"))).id

    private fun bookRequest(
        authorIds: List<Long>,
        publicationStatus: PublicationStatus = PublicationStatus.UNPUBLISHED,
        title: String = "Kotlin",
    ): BookRequest = BookRequest(title, BigDecimal("12.34"), authorIds, publicationStatus)

    private fun createBook(request: BookRequest): JsonNode {
        val result = mvc.perform(
            post("/books").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)),
        ).andExpect(status().isCreated).andReturn()
        return mapper.readTree(result.response.contentAsString)
    }

    private fun assertBadAuthor(json: String) {
        val authorId = author("Existing")
        listOf(post("/authors"), put("/authors/$authorId")).forEach { request ->
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        }
    }

    private fun assertBadBook(json: String) {
        val existing = books.create(bookRequest(listOf(author("Existing"))))
        listOf(post("/books"), put("/books/${existing.id}")).forEach { request ->
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        }
        assertThat(books.findByAuthor(existing.authors.single().id)).containsExactly(existing)
    }
}
