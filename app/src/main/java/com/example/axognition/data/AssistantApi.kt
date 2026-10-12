package com.example.axognition.data

import com.example.axognition.BuildConfig
import com.example.axognition.ui.AppLanguage
import com.example.axognition.shared.AssistantLanguage
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** The Android app calls Ktor only; Ktor keeps the LM Studio address private. */
object AssistantApi {
    data class HistoryMessage(val text: String, val fromStudent: Boolean)
    data class ScreenImage(val mimeType: String, val base64: String)

    /** Both typed and spoken questions include the selected chat's entire history. */
    internal fun requestBody(question: String, history: List<HistoryMessage>, language: String, screenImage: ScreenImage? = null): JSONObject {
        val historyJson = JSONArray()
        history.filter { it.text.isNotBlank() }.forEach { message ->
            historyJson.put(JSONObject()
                .put("role", if (message.fromStudent) "user" else "assistant")
                .put("content", message.text))
        }
        val replyLanguage = AssistantLanguage.replyLanguage(question, history.filter { it.fromStudent }.map { it.text }, language)
        return JSONObject().put("message", question).put("history", historyJson).put("language", replyLanguage).apply {
            if (screenImage != null) put("screenImage", JSONObject().put("mimeType", screenImage.mimeType).put("base64", screenImage.base64))
        }
    }

    class RequestCancellation {
        private val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile private var connection: HttpURLConnection? = null
        fun check() {
            if (cancelled.get()) throw kotlinx.coroutines.CancellationException("Voice turn cancelled")
        }
        fun attach(value: HttpURLConnection) {
            connection = value
            if (cancelled.get()) { value.disconnect(); check() }
        }
        fun cancel() {
            cancelled.set(true)
            val current = connection
            // Disconnect may block; never do it on the Android UI/audio callback thread.
            if (current != null) kotlin.concurrent.thread(isDaemon = true, name = "assistant-cancel") {
                runCatching { current.disconnect() }
            }
        }
    }

    fun sendQuestion(question: String, history: List<HistoryMessage> = emptyList(), screenImage: ScreenImage? = null): String {
        val connection = URL("${BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')}/assistant/chat")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 100_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody(question, history, AppLanguage.code, screenImage).toString())
            }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            check(connection.responseCode == 200) {
                JSONObject(body.ifBlank { "{}" }).optString("error", "The assistant is unavailable (HTTP ${connection.responseCode}).")
            }
            JSONObject(body).getString("reply")
        } finally {
            connection.disconnect()
        }
    }

    /** Receives incremental text from Ktor while the local model is generating. */
    fun streamQuestion(
        question: String,
        history: List<HistoryMessage> = emptyList(),
        cancellation: RequestCancellation = RequestCancellation(),
        screenImage: ScreenImage? = null,
        onPartialAnswer: (String) -> Unit
    ): String {
        val connection = URL("${BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')}/assistant/chat/stream")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 100_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/x-ndjson")

        return try {
            cancellation.attach(connection)
            cancellation.check()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody(question, history, AppLanguage.code, screenImage).toString())
            }
            check(connection.responseCode == 200) {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                JSONObject(errorBody.ifBlank { "{}" }).optString(
                    "error", "The assistant is unavailable (HTTP ${connection.responseCode})."
                )
            }
            val complete = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    cancellation.check()
                    if (line.isBlank()) return@forEach
                    val event = JSONObject(line)
                    val error = event.optString("error")
                    if (error.isNotBlank()) throw IllegalStateException(error)
                    val delta = event.optString("delta")
                    if (delta.isNotEmpty()) {
                        complete.append(delta)
                        onPartialAnswer(complete.toString())
                    }
                }
            }
            complete.toString().takeIf { it.isNotBlank() }
                ?: error("The assistant returned an empty response.")
        } finally {
            connection.disconnect()
        }
    }
}
