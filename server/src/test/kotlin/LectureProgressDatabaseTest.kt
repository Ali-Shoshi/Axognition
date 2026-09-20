package com.example

import com.example.db.LectureProgressRepository
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import org.flywaydb.core.Flyway
import kotlin.test.*

class LectureProgressDatabaseTest {
    /** Opt in: creates and removes ONLY a random test schema in the configured PostgreSQL database. */
    @Test fun `migration transactional retries authentication and child isolation`() {
        if (System.getenv("LECTURE_DB_TESTS") != "true") return
        val schema = "lecture_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(requiredSetting("DB_URL"), requiredSetting("DB_USER"), requiredSetting("DB_PASSWORD")).use { admin ->
            admin.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                // uuid-ossp is already installed in public on developer databases. Keep the
                // test search path isolated while supplying the same UUID-v4 default locally.
                Flyway.configure().dataSource(requiredSetting("DB_URL"), requiredSetting("DB_USER"), requiredSetting("DB_PASSWORD"))
                    .schemas(schema).baselineVersion("0").load().baseline()
                admin.createStatement().use { it.execute("CREATE FUNCTION $schema.uuid_generate_v4() RETURNS uuid LANGUAGE SQL AS 'SELECT pg_catalog.gen_random_uuid()'") }
                DatabaseFactory.connect(schema)
                val child = UUID.randomUUID(); val other = UUID.randomUUID()
                DatabaseFactory.withConnection { c ->
                    for (id in listOf(child, other)) {
                        c.prepareStatement("INSERT INTO children(child_id,first_name,last_name,date_of_birth,gender,enrollment_date) VALUES (?,'Lecture','Test','2015-01-01','FEMALE',CURRENT_DATE)").use {
                            it.setObject(1, id); it.executeUpdate()
                        }
                        c.prepareStatement("INSERT INTO child_credentials(child_id,username,password_hash) VALUES (?,?,'test-only')").use {
                            it.setObject(1, id); it.setString(2, "test$id"); it.executeUpdate()
                        }
                    }
                }
                val definition = LectureCatalog.find("geometry")!!
                val session = UUID.randomUUID().toString()
                fun event(type: String, sequence: Long) = LectureEvent(UUID.randomUUID().toString(), session, type,
                    Instant.now().toString(), lessonVersion = definition.version, sequence = sequence)
                val events = listOf(event("lecture_started", 0), event("slide_next", 1), event("slide_back", 2),
                    event("answer_submitted", 3).copy(question = 0, answer = 999.0, elapsedMs = 2300, activeMs = 2100, sinceAttemptMs = 2300))
                LectureEvents.validate(definition, events)
                val executor = Executors.newFixedThreadPool(4)
                try {
                    val futures = (1..4).map { executor.submit<LectureSyncResponse> { LectureProgressRepository.ingest(child, definition, events) } }
                    futures.forEach { assertEquals(events.map { e -> e.id }, it.get().acceptedIds) }
                } finally { executor.shutdownNow() }
                val state = LectureProgressRepository.get(child, definition)
                assertEquals(1L, state.startCount); assertEquals(1L, state.wrongAnswerCount); assertEquals(1L, state.backCount)
                assertEquals(setOf("0:0"), state.completedSlides)
                assertEquals(0L, LectureProgressRepository.get(other, definition).startCount)
                val page1 = LectureProgressRepository.history(child, definition.id, "1970-01-01T00:00:00Z", UUID(0,0), 2)
                assertEquals(2, page1["items"]!!.jsonArray.size)
                val cursor = page1["nextCursor"]!!.jsonObject
                val page2 = LectureProgressRepository.history(child, definition.id, cursor["afterTime"]!!.jsonPrimitive.content, UUID.fromString(cursor["afterId"]!!.jsonPrimitive.content), 2)
                assertEquals(2, page2["items"]!!.jsonArray.size); assertEquals(JsonNull, page2["nextCursor"])
                assertEquals(4, (page1["items"]!!.jsonArray + page2["items"]!!.jsonArray).map { it.jsonObject["eventId"] }.distinct().size)

                testApplication {
                    environment { config = MapApplicationConfig() }
                    application { configureChildAuthentication(); configureRouting() }
                    val token = ChildJwt.createToken(child.toString())
                    assertEquals(HttpStatusCode.Unauthorized, client.get("/me/lectures/geometry/progress").status)
                    assertEquals(HttpStatusCode.Unauthorized, client.get("/me/lectures/geometry/progress") { bearerAuth(ChildJwt.createToken(UUID.randomUUID().toString())) }.status)
                    val progress = client.get("/me/lectures/geometry/progress") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.OK, progress.status)
                    val json = progressJson.parseToJsonElement(progress.bodyAsText()).jsonObject
                    assertEquals(0, json["generation"]!!.jsonPrimitive.int, "Default values must be included for the player")
                    assertEquals(1, json["startCount"]!!.jsonPrimitive.int)
                    val untouched = client.get("/me/lectures/geometry/progress") { bearerAuth(ChildJwt.createToken(other.toString())) }
                    assertEquals(0, progressJson.parseToJsonElement(untouched.bodyAsText()).jsonObject["startCount"]!!.jsonPrimitive.int)
                    val retry = client.post("/me/lectures/geometry/events") {
                        bearerAuth(token); contentType(ContentType.Application.Json); setBody(progressJson.encodeToString(LectureEventBatch(events)))
                    }
                    assertEquals(HttpStatusCode.OK, retry.status)
                    assertEquals(1L, LectureProgressRepository.get(child, definition).startCount)
                    assertEquals(HttpStatusCode.BadRequest, client.post("/me/lectures/geometry/events") {
                        bearerAuth(token); contentType(ContentType.Application.Json)
                        setBody(progressJson.encodeToString(LectureEventBatch(listOf(event("slide_next", 5).copy(occurredAt = "invalid")))))
                    }.status)
                    assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/me/lectures/geometry/events") {
                        bearerAuth(token); contentType(ContentType.Application.Json); setBody(" ".repeat(262145))
                    }.status)
                    assertEquals(HttpStatusCode.NotFound, client.get("/me/lectures/missing/progress") { bearerAuth(token) }.status)
                }
                // Reset retains all four original events and lifetime counters; stale retries cannot restore progress.
                val reset = event("reset", 4)
                LectureProgressRepository.ingest(child, definition, listOf(reset))
                val stale = event("slide_next", 5)
                val after = LectureProgressRepository.ingest(child, definition, listOf(stale)).state
                assertEquals(1L, after.generation); assertTrue(after.completedSlides.isEmpty()); assertEquals(1L, after.startCount)
            } finally {
                DatabaseFactory.close()
                check(schema.matches(Regex("lecture_test_[0-9a-f]{32}")))
                admin.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
