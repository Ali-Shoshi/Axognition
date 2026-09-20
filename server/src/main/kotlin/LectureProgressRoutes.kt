package com.example

import com.example.db.ChildAccountRepository
import com.example.db.LectureProgressRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receiveText
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.encodeToJsonElement
import java.time.OffsetDateTime
import java.util.UUID

private suspend fun ApplicationCall.progressChild(): UUID? {
    val child = principal<JWTPrincipal>()?.let(::childIdFrom)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (child == null || withContext(Dispatchers.IO) { ChildAccountRepository.findProfile(child) } == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "A valid child session is required.")); return null
    }
    return child
}

fun Route.lectureProgressRoutes() {
    authenticate(ChildAuthProvider) {
        get("/me/lecture-progress") {
            val child = call.progressChild() ?: return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            val after = call.request.queryParameters["after"].orEmpty().take(80)
            call.respond(withContext(Dispatchers.IO) { LectureProgressRepository.list(child, after, limit) })
        }
        route("/me/lectures/{lectureId}") {
            install(RequestBodyLimit) { bodyLimit { 262144L } }
            get("/progress") {
                val child = call.progressChild() ?: return@get
                val definition = LectureCatalog.find(call.parameters["lectureId"].orEmpty()) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(progressJson.encodeToJsonElement(withContext(Dispatchers.IO) { LectureProgressRepository.get(child, definition) }))
            }
            post("/events") {
                val child = call.progressChild() ?: return@post
                val definition = LectureCatalog.find(call.parameters["lectureId"].orEmpty()) ?: return@post call.respond(HttpStatusCode.NotFound)
                // Also bounded by RequestBodyLimit installed on these routes below.
                val body = call.receiveText()
                if (body.length > 262144) return@post call.respond(HttpStatusCode.PayloadTooLarge)
                val events = try {
                    progressJson.decodeFromString<LectureEventBatch>(body).events.also { LectureEvents.validate(definition, it) }
                } catch (_: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid lecture event batch."))
                }
                call.respond(progressJson.encodeToJsonElement(withContext(Dispatchers.IO) { LectureProgressRepository.ingest(child, definition, events) }))
            }
            get("/events") {
                val child = call.progressChild() ?: return@get
                val definition = LectureCatalog.find(call.parameters["lectureId"].orEmpty()) ?: return@get call.respond(HttpStatusCode.NotFound)
                val afterTime = call.request.queryParameters["afterTime"] ?: "1970-01-01T00:00:00Z"
                val afterId = runCatching { UUID.fromString(call.request.queryParameters["afterId"] ?: "00000000-0000-0000-0000-000000000000") }.getOrNull()
                if (afterId == null || runCatching { OffsetDateTime.parse(afterTime) }.isFailure) return@get call.respond(HttpStatusCode.BadRequest)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                call.respond(withContext(Dispatchers.IO) { LectureProgressRepository.history(child, definition.id, afterTime, afterId, limit) })
            }
        }
    }
}
