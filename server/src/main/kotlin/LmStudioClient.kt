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

    suspend fun answer(question: String): String = withContext(Dispatchers.IO) {
        val requestJson = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.4)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are Axognition's friendly learning assistant. Give clear, age-appropriate answers. If you are unsure, say so.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", question)
                })
            })
        }

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
}
