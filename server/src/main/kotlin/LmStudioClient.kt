package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Talks to the local LM Studio process. This service is deliberately accessed only
 * from Ktor, so the tablet never needs access to LM Studio's port.
 */
object LmStudioClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val baseUrl: String
        get() = settingOrDefault("LM_STUDIO_URL", "http://127.0.0.1:1234/v1").trimEnd('/')

    private val modelName: String
        get() = settingOrDefault("LM_STUDIO_MODEL", "BEST-qwen_qwen3.5-2b")

    private fun requestBody(
        question: String,
        history: List<AssistantHistoryMessage>,
        language: String,
        stream: Boolean
    ) = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.4)
            put("max_tokens", 220)
            put("stream", stream)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are Axognition's friendly learning assistant. Give clear, age-appropriate answers. If you are unsure, say so. Make answers very short and direct to the question, max 3 sentences, never use symbol * on the response. " +
                        if (language == "sq") "Respond in Albanian (Shqip), using correct ë and ç, unless the student explicitly requests another language." else "Respond in English unless the student explicitly requests another language.")
                })
                history.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", question)
                })
            })
        }

    suspend fun answer(question: String, history: List<AssistantHistoryMessage> = emptyList(), language: String = "en"): String = withContext(Dispatchers.IO) {
        val requestJson = requestBody(question, history, language, stream = false)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "LM Studio returned HTTP ${response.statusCode()}. Check that its local server is running and the model is loaded."
        }

        Json.parseToJsonElement(response.body())
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("LM Studio returned no assistant message.")
    }

    /** Streams OpenAI-compatible SSE deltas from the local LM Studio process. */
    suspend fun streamAnswer(
        question: String,
        history: List<AssistantHistoryMessage> = emptyList(),
        language: String = "en",
        onDelta: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(question, history, language, stream = true).toString()))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in 200..299) {
            "LM Studio returned HTTP ${response.statusCode()}. Check that its local server is running and the model is loaded."
        }

        val complete = StringBuilder()
        response.body().bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val payload = line.removePrefix("data:").trim()
                if (!line.startsWith("data:") || payload.isBlank()) continue
                if (payload == "[DONE]") break
                val delta = runCatching {
                    Json.parseToJsonElement(payload)
                        .jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("delta")
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull().orEmpty()
                if (delta.isNotEmpty()) {
                    complete.append(delta)
                    onDelta(delta)
                }
            }
        }
        complete.toString().trim().takeIf { it.isNotEmpty() }
            ?: error("LM Studio returned no assistant message.")
    }
}
