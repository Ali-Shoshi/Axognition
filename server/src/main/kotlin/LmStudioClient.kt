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

/** Preserve the complete conversation; never accept a system role from chat history. */
internal fun assistantConversationHistory(history: List<AssistantHistoryMessage>): List<AssistantHistoryMessage> =
    history.filter { it.role in setOf("user", "assistant") && it.content.isNotBlank() }

private val detailedAnswerRequest = Regex(
    """\b(?:more detail|in (?:more )?detail|detailed (?:explanation|answer|response)|step[\s-]+by[\s-]+step|show (?:me )?(?:the |your )?(?:steps|work)|walk me through|tell me more|explain more|expand (?:on|this|your)|longer (?:answer|explanation|response)|more examples|additional examples)\b|\b(?:at least )?(?:[1-9]|\d{2,}|one|two|three|four|five|six|seven|eight|nine|ten)\s+(?:sentences?|paragraphs?)\b|më shumë|me hollësi|(?:në|me) detaje|hap pas hapi|trego hapat|shpjego më gjatë|shpjegim të (?:gjatë|hollësishëm|detajuar)|^\s*more[.!?]*\s*$""",
    RegexOption.IGNORE_CASE
)
private val briefAnswerRequest = Regex(
    """\b(?:keep (?:it|the answer) short|briefly|short answer|concise answer)\b|përgjigje të shkurtër|shpjego shkurt""",
    RegexOption.IGNORE_CASE
)

internal fun assistantAnswerStyle(question: String): String =
    if (detailedAnswerRequest.containsMatchIn(question) && !briefAnswerRequest.containsMatchIn(question)) {
        "Follow the student's requested level of detail, length or format. Give only the explanation, steps or examples requested, in simple conversational language. " +
            "Keep each point brief and stop when the request is answered. Follow a specific requested length or format, including a minimum sentence count."
    } else {
        "Answer directly in one short sentence; add a second only if needed to answer the question. Aim for 10–20 words, at most 25 words in total. " +
            "Give only the answer and any essential explanation, then stop. " +
            "Examples of the intended brevity: 'How are you?' -> 'Doing well, thanks!' " +
            "'What is a triangle?' -> 'A shape with three straight sides.' " +
            "Do not end with an offer to help or a question to keep the conversation going. " +
            "If the student explicitly requests a different length or format, follow that request."
    }

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

    internal fun requestBody(
        question: String,
        history: List<AssistantHistoryMessage>,
        language: String,
        stream: Boolean
    ) = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.4)
            put("reasoning_effort", "none")
            put("stream", stream)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are Axognition's friendly learning assistant. Talk naturally using simple, age-appropriate words, like a helpful person in a conversation. " +
                        "Start with the answer. Skip greetings, introductions, repeating the question, unnecessary examples, summaries and unsolicited follow-up questions. " +
                        "Use the earlier student questions and assistant answers in this conversation as context. " +
                        "Use the previous topic to understand follow-ups such as 'why?', 'that', or 'give another example'. " +
                        "Remember relevant facts and corrections the student has provided, and avoid repeating explanations unless asked. " +
                        "If the available conversation does not make a follow-up clear, ask one brief clarifying question instead of guessing. " +
                        assistantAnswerStyle(question) + " " +
                        "If you are unsure, say so briefly. Use plain text suitable for reading aloud, without Markdown or LaTeX formatting. " +
                        if (language == "sq") "Respond in Albanian (Shqip), using correct ë and ç, unless the student explicitly requests another language." else "Respond in English unless the student explicitly requests another language.")
                })
                assistantConversationHistory(history).forEach { message ->
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
