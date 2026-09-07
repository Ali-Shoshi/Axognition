package com.example

import com.example.db.BookRepository
import com.example.db.ChildAccountRepository
import com.example.db.ChildProfile
import com.example.db.CourseRepository
import com.example.db.NewBook
import com.example.db.StoredBook
import com.example.db.SubjectRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateCourseRequest(
    val title: String,
    val description: String? = null
)

@Serializable
data class CourseResponse(
    val id: Long,
    val title: String,
    val description: String?
)

@Serializable
data class CreateBookRequest(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val category: String,
    val format: String,
    val objectKey: String,
    val coverObjectKey: String? = null,
    val isDownloadable: Boolean = true,
    val lectureUnitId: String? = null,
    val courseId: Long? = null
)

@Serializable
data class BookResponse(
    val id: String,
    val title: String,
    val author: String?,
    val description: String?,
    val category: String,
    val format: String,
    val fileSizeBytes: Long,
    val isDownloadable: Boolean,
    val coverUrl: String? = null
)

@Serializable
data class AssistantHistoryMessage(val role: String, val content: String)

@Serializable
data class AssistantChatRequest(val message: String, val history: List<AssistantHistoryMessage> = emptyList())

@Serializable
data class AssistantChatResponse(val reply: String)

@Serializable
data class ChildLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class ChildProfileResponse(
    val childId: String,
    val displayName: String,
    val grade: Int?
)

@Serializable
data class ChildLoginResponse(
    val accessToken: String,
    val child: ChildProfileResponse
)

private fun ChildProfile.toResponse() = ChildProfileResponse(
    childId = childId.toString(),
    displayName = displayName,
    grade = grade
)

private fun StoredBook.toResponse(coverUrl: String? = null) = BookResponse(
    id = id.toString(), title = title, author = author, description = description,
    category = category, format = format, fileSizeBytes = fileSizeBytes,
    isDownloadable = isDownloadable, coverUrl = coverUrl
)

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Axognition server is running")
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        post("/auth/child/login") {
            val request = call.receive<ChildLoginRequest>()
            val username = request.username.trim()
            if (username.length !in 3..50 || request.password.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Enter a username and password."))
            }
            val account = withContext(Dispatchers.IO) { ChildAccountRepository.findByUsername(username) }
            if (account == null || !PasswordHasher.matches(request.password, account.passwordHash)) {
                return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "The username or password is incorrect."))
            }
            withContext(Dispatchers.IO) { ChildAccountRepository.recordSuccessfulLogin(account.childId) }
            call.respond(
                ChildLoginResponse(
                    accessToken = ChildJwt.createToken(account.childId.toString()),
                    child = ChildProfile(account.childId, account.displayName, account.grade).toResponse()
                )
            )
        }

        authenticate(ChildAuthProvider) {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val childId = childIdFrom(principal)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val profile = withContext(Dispatchers.IO) { ChildAccountRepository.findProfile(childId) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "The child account is unavailable."))
                call.respond(profile.toResponse())
            }
        }

        post("/assistant/chat") {
            val request = call.receive<AssistantChatRequest>()
            val message = request.message.trim()
            if (message.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message must not be blank"))
            }
            if (message.length > 4_000) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message is too long"))
            }

            val history = request.history
                .filter { it.role in setOf("user", "assistant") && it.content.isNotBlank() }
                .takeLast(12)
            val reply = runCatching { LmStudioClient.answer(message, history) }
                .getOrElse { error ->
                    application.log.warn("LM Studio chat request failed", error)
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "The local learning assistant is unavailable. Check that LM Studio is running and the model is loaded.")
                    )
                }
            call.respond(AssistantChatResponse(reply))
        }

        get("/subjects") {
            val grade = call.request.queryParameters["grade"]?.toIntOrNull()
            if (grade == null || grade !in 1..9) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "grade must be between 1 and 9"))
            }
            call.respond(withContext(Dispatchers.IO) { SubjectRepository.forGrade(grade) })
        }

        get("/courses") {
            val courses = withContext(Dispatchers.IO) {
                CourseRepository.findAll()
            }
            call.respond(courses)
        }

        post("/courses") {
            val request = call.receive<CreateCourseRequest>()
            val course = withContext(Dispatchers.IO) {
                CourseRepository.create(request.title, request.description)
            }
            call.respond(HttpStatusCode.Created, course)
        }

        get("/books") {
            val books = withContext(Dispatchers.IO) {
                BookRepository.findAll().map { book ->
                    book.toResponse(book.coverObjectKey?.let(ObjectStorage::createBookDownloadUrl))
                }
            }
            call.respond(books)
        }

        // Development-only endpoint. Authentication will restrict this to admins later.
        post("/books") {
            val request = call.receive<CreateBookRequest>()
            val book = withContext(Dispatchers.IO) {
                val objectMetadata = ObjectStorage.statBook(request.objectKey)
                BookRepository.create(
                    NewBook(
                        title = request.title, author = request.author,
                        description = request.description, category = request.category,
                        format = request.format, objectKey = request.objectKey,
                        coverObjectKey = request.coverObjectKey,
                        contentType = objectMetadata.contentType,
                        fileSizeBytes = objectMetadata.sizeBytes,
                        isDownloadable = request.isDownloadable,
                        lectureUnitId = request.lectureUnitId?.let(UUID::fromString),
                        courseId = request.courseId
                    )
                )
            }
            call.respond(HttpStatusCode.Created, book.toResponse())
        }

        // Development-only endpoint. The objectKey must start with covers/.
        post("/books/{id}/cover") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book id"))
            if (withContext(Dispatchers.IO) { BookRepository.findById(id) } == null) {
                return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
            }
            val objectKey = call.request.queryParameters["objectKey"]
                ?.takeIf { it.startsWith("covers/") }
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "objectKey must start with covers/"))

            var uploaded = false
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && !uploaded) {
                    withContext(Dispatchers.IO) {
                        part.provider().toInputStream().use { stream ->
                            ObjectStorage.uploadBookCover(
                                objectKey, stream, part.contentType?.toString() ?: "image/jpeg"
                            )
                        }
                    }
                    uploaded = true
                }
                part.dispose()
            }
            if (!uploaded) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Choose one cover image file"))
            }
            val book = withContext(Dispatchers.IO) { BookRepository.updateCover(id, objectKey) }
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
            call.respond(book.toResponse(ObjectStorage.createBookDownloadUrl(objectKey)))
        }

        get("/books/{id}/download") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book id"))
            val book = withContext(Dispatchers.IO) { BookRepository.findById(id) }
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
            if (!book.isDownloadable) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "This book is not downloadable"))
            }
            val downloadUrl = withContext(Dispatchers.IO) {
                ObjectStorage.createBookDownloadUrl(book.objectKey)
            }
            call.respond(mapOf("downloadUrl" to downloadUrl))
        }
    }
}
