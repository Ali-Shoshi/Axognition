package com.example.axognition.data

import com.example.axognition.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The Android app calls Ktor only; Ktor keeps the LM Studio address private. */
object AssistantApi {
    fun sendQuestion(question: String): String {
        val connection = URL("${BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')}/assistant/chat")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 100_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(JSONObject().put("message", question).toString())
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
}
